package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.domain.cast.NvaFrame
import kotlinx.coroutines.flow.SharedFlow

/**
 * 投屏长连接的抽象，供表现层会话控制器使用，避免其直接依赖 infra.cast 实现。
 * infra 侧 NvaClient 实现本接口；连接/指令语义与具体协议实现无关。
 */
interface CastClient {
    val tvFrames: SharedFlow<NvaFrame>
    val replies: SharedFlow<NvaFrame.Reply>
    val disconnected: SharedFlow<String>
    suspend fun connect(host: String, port: Int): Outcome<Unit>
    suspend fun sendCommand(name: String, json: String? = null): Outcome<Unit>
    fun stopAndClose()
    fun close()
}
