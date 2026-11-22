package com.aritxonly.bdfun.core.model.cast

/**
 * 局域网里发现的一台投屏接收设备。
 *
 * 这是纯数据模型：协议细节（SSDP / XML / NVA TCP）留在 infra:cast，界面只依赖这一层。
 * 本项目的投屏走 B 站「哔哩必连」NVA 协议：电视自己取流，手机只发播放/遥控/弹幕指令。
 */
data class CastDevice(
    /** UPnP 唯一设备名，来自设备描述的 UDN；同一台设备多次发现应归并到同一个 UDN。 */
    val udn: String,
    /** 用户在电视上看到的名称，来自 friendlyName。 */
    val name: String,
    /** 设备描述文档的 URL（SSDP 响应里的 LOCATION）。 */
    val location: String,
    /** 设备类型，MediaRenderer:1 或:2。 */
    val deviceType: String,
    /** 厂商与型号，仅用于扫描页副标题展示。 */
    val manufacturer: String = "",
    val modelName: String = "",
    /**
     * NVA 长连接的电视端主机（从 LOCATION 的 host 提取）与端口。
     * NVA 走 TCP，端口通常是 9958。没有 host 说明设备不支持 NVA，无法投屏。
     */
    val nvaHost: String = "",
    val nvaPort: Int = DEFAULT_NVA_PORT,
    /** 是否支持 B 站 NVA 协议（设备描述里含 NirvanaControl 服务）。只有 NVA 设备能真正投屏。 */
    val isNva: Boolean = false,
) {
    val canCast get() = isNva && nvaHost.isNotBlank()

    companion object {
        const val DEFAULT_NVA_PORT = 9958
    }
}
