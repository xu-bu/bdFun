package com.aritxonly.bdfun.presentation.cast

import com.aritxonly.bdfun.core.application.CastClient
import com.aritxonly.bdfun.core.application.PlaybackController
import com.aritxonly.bdfun.core.common.AppError
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.domain.VideoPlaybackRepository
import com.aritxonly.bdfun.core.domain.VideoRepository
import com.aritxonly.bdfun.core.domain.cast.NvaFrame
import com.aritxonly.bdfun.core.domain.cast.NvaQualityOption
import com.aritxonly.bdfun.core.domain.cast.nvaDanmakuSwitchPayload
import com.aritxonly.bdfun.core.domain.cast.nvaPlayPayload
import com.aritxonly.bdfun.core.domain.cast.nvaQualityPayload
import com.aritxonly.bdfun.core.domain.cast.nvaSeekPayload
import com.aritxonly.bdfun.core.domain.cast.nvaSendDanmakuPayload
import com.aritxonly.bdfun.core.domain.cast.nvaSpeedPayload
import com.aritxonly.bdfun.core.domain.cast.parseNvaPlayState
import com.aritxonly.bdfun.core.domain.cast.parseNvaProgress
import com.aritxonly.bdfun.core.domain.cast.parseNvaQuality
import com.aritxonly.bdfun.core.model.cast.CastDevice
import com.aritxonly.bdfun.core.model.video.DanmakuItem
import com.aritxonly.teak.common.settings.TeakDebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 投屏会话状态。UI 按这几种状态渲染，避免"点了没反应"。 */
data class CastSessionState(
    val device: CastDevice? = null,
    val status: CastStatus = CastStatus.Idle,
    val title: String = "",
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val playing: Boolean = false,
    /** 当前生效的清晰度 qn：以电视 OnQnSwitch 的 curQn 为准，切换时先乐观更新。 */
    val quality: Int = 64,
    /** 电视报告的本片源支持档位（含 displayDesc / needVip）。 */
    val qualityOptions: List<NvaQualityOption> = emptyList(),
    val speed: Float = 1f,
    val danmakuOn: Boolean = true,
    val message: String? = null,
)

enum class CastStatus { Idle, Connecting, Playing, Error }

/**
 * 投屏会话编排（B 站 NVA 协议）。
 *
 * 与 DLNA 的本质区别：电视自己按 aid/cid 取流，手机只发 Play/遥控/弹幕指令，
 * 不需要本地流代理，也没有 Referer 问题。手机通过 TV 推来的 OnProgress 同步进度，
 * 并按进度把本地弹幕逐条 SendDanmaku 给电视渲染（需 TV 端登录）。
 *
 * 会话是**进程级**的：扫描页 CastActivity 选完设备就 finish 回视频页，
 * 之后由视频页自己驱动电视（静音 + 拖进度同步）。所以本类不能挂在 Activity 的
 * lifecycleScope 上，scope 由 AppContainer 用 SupervisorJob 提供。
 *
 * 分层：这是 app 层用例编排（组合多个仓储 + NVA 客户端），不放 core:domain。
 */
internal class CastSessionController(
    private val videoRepository: VideoRepository,
    private val videoPlaybackRepository: VideoPlaybackRepository,
    private val accessKey: () -> String,
    private val clientFactory: () -> CastClient,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(CastSessionState())
    val state: StateFlow<CastSessionState> = mutable.asStateFlow()

    private var client: CastClient? = null
    private var framesJob: Job? = null
    private var disconnectJob: Job? = null
    /** 已推送弹幕的游标：TV 进度到哪就推到哪，避免一次性灌满电视。 */
    private var danmakuCursor = 0
    private var danmakuList: List<DanmakuItem> = emptyList()

    /** 视频页的播放器：投屏成功后静音、拖动进度和暂停/恢复都同步到电视。 */
    private var phonePlayback: PlaybackController? = null
    /**
     * 手机播放器作为主控：seek → 电视 Seek，暂停/恢复 → 电视 Pause/Resume。
     * 只在投屏播放中转发；电视推来的 OnPlayState 只更新本类 state、不会回调播放器，
     * 因此不会形成"电视→手机→电视"的循环。
     */
    private val phoneListener = object : PlaybackController.Listener {
        override fun onSeek(positionMs: Long) {
            if (mutable.value.status == CastStatus.Playing) {
                seek((positionMs / 1000).toInt())
            }
        }

        override fun onPlayingChanged(playing: Boolean) {
            if (mutable.value.status != CastStatus.Playing) return
            if (playing) play() else pause()
        }
    }

    /** 视频页组合时绑定播放器；退出视频页解绑。Dry-run 的扫描页没有播放器也不影响。 */
    fun attachPhonePlayback(playback: PlaybackController) {
        if (phonePlayback === playback) return
        phonePlayback?.removeListener(phoneListener)
        phonePlayback = playback
        playback.addListener(phoneListener)
        // 若投屏在视频页重建之前就已开始，补上静音。
        if (mutable.value.status == CastStatus.Playing) playback.setMuted(true)
    }

    fun detachPhonePlayback(playback: PlaybackController) {
        if (phonePlayback !== playback) return
        playback.removeListener(phoneListener)
        // 投屏还在继续就把音量还回去，避免之后新页面无声。
        if (mutable.value.status == CastStatus.Playing) playback.setMuted(false)
        phonePlayback = null
    }

    /**
     * 选中设备后调用：连接 → 取详情 → 从手机当前进度 Play → 拉弹幕 → 监听状态。
     * bvid/cid/startSec/quality 由视频页经路由传入，不再是会话构造参数。
     */
    fun start(device: CastDevice, bvid: String, cid: Long, startSec: Int, quality: Int) {
        if (mutable.value.status == CastStatus.Connecting) return
        cleanup()
        mutable.value = CastSessionState(
            device = device, status = CastStatus.Connecting,
            quality = quality, message = "正在连接电视…",
        )
        scope.launch {
            try {
                if (!device.canCast) {
                    return@launch fail("这台设备不支持哔哩必连投屏，请使用云视听小电视等 B 站 TV 端")
                }
                // 1. 连 NVA 长连接并握手
                val nva = clientFactory()
                val connected = nva.connect(device.nvaHost, device.nvaPort)
                when (connected) {
                    is Outcome.Failure -> return@launch fail((connected.error as? AppError.Network)?.message ?: "连接失败")
                    is Outcome.Success<*> -> Unit
                }
                client = nva
                observeFrames(nva)

                // 2. 视频详情（Play 需要 aid + cid；用路由带来的 cid，分 P 才准）
                val detailResult = videoRepository.videoDetail(bvid)
                val detail = when (detailResult) {
                    is Outcome.Success -> detailResult.value
                    is Outcome.Failure -> return@launch fail("获取视频信息失败：${errText(detailResult)}")
                }
                val effectiveCid = if (cid > 0) cid else detail.cid

                // 3. 发 Play：aid/cid/清晰度/起始进度/凭据。电视自己取流。
                // 起始进度取手机这一刻的播放位置（扫描期间用户可能又拖了），没有则退回路由值。
                val liveSec = (phonePlayback?.refresh()?.positionMs ?: 0L).div(1000).toInt()
                val effectiveStartSec = if (liveSec > 0) liveSec else startSec
                val playPayload = nvaPlayPayload(
                    aid = detail.aid, cid = effectiveCid, quality = quality,
                    seekSec = effectiveStartSec, accessKey = accessKey(),
                )
                TeakDebugLog.record("CAST", "Play payload = $playPayload")
                val play = nva.sendCommand("Play", playPayload)
                if (play is Outcome.Failure) return@launch fail("推送播放失败：${errText(play)}")

                // 电视开始播后把手机静音：同一个视频别出两路声音。
                phonePlayback?.setMuted(true)
                mutable.value = mutable.value.copy(
                    status = CastStatus.Playing, title = detail.title,
                    playing = true, positionSec = effectiveStartSec,
                    message = "已投屏到 ${device.name}，拖动手机进度可同步电视",
                )

                // 4. 拉弹幕，等进度推来时按时间逐条发送（TV 端需登录才显示）
                when (val d = videoPlaybackRepository.danmaku(effectiveCid, detail.duration)) {
                    is Outcome.Success -> {
                        danmakuList = d.value.sortedBy { it.timeMs }
                        danmakuCursor = 0
                    }
                    is Outcome.Failure -> Unit // 弹幕拿不到不影响主播放
                }
            } catch (e: Exception) {
                fail("投屏失败：${e.message ?: "未知错误"}")
            }
        }
    }

    /** 监听 TV 推来的帧：进度、播放状态、断线。 */
    private fun observeFrames(nva: CastClient) {
        framesJob?.cancel()
        framesJob = scope.launch {
            launch {
                nva.tvFrames.collect { frame ->
                    if (frame !is NvaFrame.Command) return@collect
                    TeakDebugLog.record("CAST", "TV frame ${frame.name}: ${frame.json ?: "<empty>"}")
                    when (frame.name) {
                        "OnProgress" -> {
                            val (position, duration) = parseNvaProgress(frame.json)
                            mutable.value = mutable.value.copy(
                                positionSec = position,
                                durationSec = if (duration > 0) duration else mutable.value.durationSec,
                            )
                            pushDueDanmaku(position)
                        }
                        "OnPlayState" -> parseNvaPlayState(frame.json)?.let { state ->
                            // 3 加载中 4 播放中 5 暂停 6 结束 7 停止
                            mutable.value = mutable.value.copy(playing = state == 4)
                        }
                        // 电视实际生效的清晰度：用它校正 UI，而不是停留在我们请求的值
                        "OnQnSwitch", "OnEpisodeSwitch" -> parseNvaQuality(frame.json)?.let { info ->
                            mutable.value = mutable.value.copy(
                                quality = if (info.curQn > 0) info.curQn else mutable.value.quality,
                                qualityOptions = info.options.ifEmpty { mutable.value.qualityOptions },
                            )
                        }
                    }
                }
            }
            launch {
                nva.replies.collect { reply ->
                    TeakDebugLog.record("CAST", "TV reply #${reply.seq}: ${reply.json ?: "<empty>"}")
                }
            }
            launch {
                nva.disconnected.collect { reason ->
                    if (mutable.value.status == CastStatus.Playing) {
                        fail(reason)
                    }
                }
            }
        }
    }

    /** 把进度之前尚未推送的弹幕发出去（TV 按自身时间轴渲染）。 */
    private fun pushDueDanmaku(positionSec: Int) {
        val client = client ?: return
        val nowMs = positionSec * 1000L
        while (danmakuCursor < danmakuList.size && danmakuList[danmakuCursor].timeMs <= nowMs) {
            val item = danmakuList[danmakuCursor]
            danmakuCursor++
            if (!mutable.value.danmakuOn) continue
            scope.launch { client.sendCommand("SendDanmaku", nvaSendDanmakuPayload(item)) }
        }
    }

    fun play() = send("Resume") { copy(playing = true, message = null) }
    fun pause() = send("Pause") { copy(playing = false, message = null) }

    fun seek(positionSec: Int) = send("Seek", nvaSeekPayload(positionSec)) {
        copy(positionSec = positionSec, message = null).also { danmakuCursor = 0 }
    }

    fun setQuality(qn: Int) = send("SwitchQn", nvaQualityPayload(qn)) { copy(quality = qn, message = null) }
    fun setSpeed(speed: Float) = send("SwitchSpeed", nvaSpeedPayload(speed.toString())) { copy(speed = speed, message = null) }
    fun setDanmaku(on: Boolean) = send("SwitchDanmaku", nvaDanmakuSwitchPayload(on)) { copy(danmakuOn = on, message = null) }

    /** 主动断开：停 TV 播放 + 关连接 + 停监听 + 手机恢复出声。 */
    fun disconnect() {
        cleanup()
        phonePlayback?.setMuted(false)
        mutable.value = CastSessionState()
    }

    private fun send(name: String, json: String? = null, update: CastSessionState.() -> CastSessionState) {
        val c = client
        if (c == null || mutable.value.status != CastStatus.Playing) return
        scope.launch {
            when (val r = c.sendCommand(name, json)) {
                is Outcome.Success<*> -> mutable.value = mutable.value.update()
                is Outcome.Failure -> mutable.value = mutable.value.copy(message = errText(r))
            }
        }
    }

    private fun cleanup() {
        framesJob?.cancel()
        framesJob = null
        disconnectJob?.cancel()
        disconnectJob = null
        // 关键：Stop 必须走 NvaClient 自己的作用域。这里原来用 lifecycleScope.launch，
        // 而"停止投屏并退出"会 finish()，Activity 销毁即取消该 scope，
        // Stop 帧永远写不到电视，表现为"界面退出但电视还在播"。
        client?.stopAndClose()
        client = null
        danmakuList = emptyList()
        danmakuCursor = 0
    }

    private fun fail(message: String) {
        // 连上之前/播放中出错都要把手机声音还回去，否则用户只剩静音画面。
        phonePlayback?.setMuted(false)
        mutable.value = mutable.value.copy(status = CastStatus.Error, playing = false, message = message)
    }

    private fun errText(result: Outcome<*>) = when (result) {
        is Outcome.Failure -> (result.error as? AppError.Network)?.message
            ?: (result.error as? AppError.Unknown)?.message
            ?: (result.error as? AppError.Parsing)?.message
            ?: "操作失败"
        else -> ""
    }
}
