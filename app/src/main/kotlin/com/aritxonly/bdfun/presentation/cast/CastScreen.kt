package com.aritxonly.bdfun.presentation.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.domain.cast.CastDeviceRepository
import com.aritxonly.bdfun.core.model.cast.CastDevice
import com.aritxonly.teak.common.chrome.TeakChromeDefaults
import com.aritxonly.teak.common.icons.AppGlyph
import com.aritxonly.teak.common.icons.LucideIcons

/**
 * 投屏页入口：只做设备扫描。选中设备、连接并开始播放后由 [CastActivity] 立刻 finish
 * 回视频页，之后的控制（暂停/进度/退出）都在视频页完成，因此这里不再保留遥控页。
 * 会话是进程级的，页面销毁不会断开投屏。
 */
@Composable
internal fun CastScreenRoute(
    repository: CastDeviceRepository,
    session: CastSessionController,
    onSelect: (CastDevice) -> Unit,
) {
    val sessionState by session.state.collectAsState()
    val holder = remember(repository) { CastScanStateHolder(repository) }
    val scanState by holder.state.collectAsState()

    LaunchedEffect(Unit) { holder.scan() }
    CastScanScreen(
        scanning = scanState.scanning,
        devices = scanState.devices,
        error = scanState.error ?: sessionState.message?.takeIf { sessionState.status == CastStatus.Error },
        connecting = sessionState.message?.takeIf { sessionState.status == CastStatus.Connecting },
        onRescan = { holder.scan() },
        onSelect = onSelect,
        onDismissError = { holder.clearError() },
    )
}

@Composable
private fun CastScanScreen(
    scanning: Boolean,
    devices: List<CastDevice>,
    error: String?,
    connecting: String?,
    onRescan: () -> Unit,
    onSelect: (CastDevice) -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // 外壳的返回键 / 标题画在状态栏下方的顶栏里，正文必须让出这段高度，
            // 否则第一行 label 会和返回键重叠。
            .padding(top = TeakChromeDefaults.TopBarHeight)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = "搜索同一 WiFi 下的投屏设备（云视听小电视等 B 站 TV 端）",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRescan, enabled = !scanning && connecting == null) {
                Text(if (scanning) "正在搜索…" else "重新搜索")
            }
            if (scanning) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
            }
        }
        connecting?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.primary)
                Spacer(Modifier.width(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.primary)
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissError) { Text("知道了") }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))

        if (devices.isEmpty() && !scanning) {
            Spacer(Modifier.height(40.dp))
            Text(
                text = "没有发现投屏设备\n请确认电视/盒子装了云视听小电视并打开，且与手机连接同一 WiFi",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(devices, key = { it.udn }) { device -> DeviceRow(device = device, onClick = { onSelect(device) }) }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: CastDevice, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(icon = LucideIcons.Cast, tint = colors.primary, size = 24.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = buildList {
                if (device.manufacturer.isNotBlank()) add(device.manufacturer)
                if (device.modelName.isNotBlank()) add(device.modelName)
                add(device.nvaHost)
            }.distinct().joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (!device.isNva) {
            Text("不支持", style = MaterialTheme.typography.labelSmall, color = colors.error)
        }
    }
}
