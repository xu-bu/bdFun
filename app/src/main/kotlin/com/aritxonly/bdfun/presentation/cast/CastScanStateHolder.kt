package com.aritxonly.bdfun.presentation.cast

import com.aritxonly.bdfun.core.common.AppError
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.domain.cast.CastDeviceRepository
import com.aritxonly.bdfun.core.model.cast.CastDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 扫描页状态：区分扫描中/出错/结果，避免"加载中就是空白"的老问题。 */
data class CastScanState(
    val scanning: Boolean = false,
    val devices: List<CastDevice> = emptyList(),
    val error: String? = null,
    val selected: CastDevice? = null,
)

/** 投屏扫描的状态持有者。发现一台就往列表里追加一台（按 UDN 去重），搜索窗口结束后收尾。 */
internal class CastScanStateHolder(
    private val repository: CastDeviceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(CastScanState())
    val state: StateFlow<CastScanState> = mutable.asStateFlow()

    fun scan(timeoutMs: Long = 4_000L) {
        if (mutable.value.scanning) return
        mutable.value = CastScanState(scanning = true)
        scope.launch {
            val result = repository.discover(timeoutMs) { device ->
                val current = mutable.value
                if (current.devices.none { it.udn == device.udn }) {
                    mutable.value = current.copy(devices = current.devices + device)
                }
            }
            val current = mutable.value
            mutable.value = when (result) {
                is Outcome.Success -> current.copy(scanning = false)
                is Outcome.Failure -> current.copy(
                    scanning = false,
                    error = (result.error as? AppError.Network)?.message ?: "投屏设备搜索失败",
                )
            }
        }
    }

    fun select(device: CastDevice) {
        mutable.value = mutable.value.copy(selected = device)
    }

    fun clearError() {
        mutable.value = mutable.value.copy(error = null)
    }
}
