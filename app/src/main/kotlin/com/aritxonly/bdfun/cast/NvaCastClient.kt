package com.aritxonly.bdfun.cast

import com.aritxonly.bdfun.core.application.CastClient
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.domain.cast.NvaFrame
import com.aritxonly.bdfun.infra.cast.NvaClient
import kotlinx.coroutines.flow.SharedFlow

/** 把 infra 的 NvaClient 适配成应用层 CastClient，隔离表现层对具体协议的依赖。 */
internal class NvaCastClient(private val client: NvaClient) : CastClient {
    override val tvFrames: SharedFlow<NvaFrame> get() = client.tvFrames
    override val replies: SharedFlow<NvaFrame.Reply> get() = client.replies
    override val disconnected: SharedFlow<String> get() = client.disconnected
    override suspend fun connect(host: String, port: Int): Outcome<Unit> = client.connect(host, port)
    override suspend fun sendCommand(name: String, json: String?): Outcome<Unit> = client.sendCommand(name, json)
    override fun stopAndClose() = client.stopAndClose()
    override fun close() = client.close()
}
