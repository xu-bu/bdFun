package com.aritxonly.bdfun.core.domain.cast

import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.model.cast.CastDevice

/**
 * 投屏设备发现接口：实现放在 infra:cast，界面只依赖这层。
 *
 * 本轮只到"发现"——[discover] 返回同一局域网内的 MediaRenderer 列表；
 * 真正的 AVTransport 推送/控制在后续接入，届时在同一个端口上扩展，不改动界面依赖。
 */
interface CastDeviceRepository {
    /**
     * 搜索局域网投屏设备。
     *
     * @param timeoutMs 搜索窗口时长；设备在窗口内陆续应答，方法会等到窗口结束再返回完整列表。
     * @param onDevice 发现一台就实时回调一次，供扫描页逐个上屏，而不是干等一个总数。
     */
    suspend fun discover(timeoutMs: Long = 4_000L, onDevice: (CastDevice) -> Unit): Outcome<Unit>
}
