package com.aritxonly.bdfun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.aritxonly.bdfun.core.navigation.Route
import com.aritxonly.bdfun.navigation.readRouteIntent
import com.aritxonly.bdfun.presentation.cast.CastScreenRoute
import com.aritxonly.bdfun.presentation.cast.CastStatus
import com.aritxonly.bdfun.ui.ActivityPageOptions
import com.aritxonly.bdfun.ui.TeakActivityScaffold

/**
 * 投屏宿主：只负责扫描设备。选中设备、会话开始后立刻 finish 回视频页，
 * 之后由视频页驱动电视（静音 + 拖动进度同步），会话本身是进程级的。
 */
class CastActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppGraph.ensure(applicationContext)
        val settings = AppGraph.settings
        val route = readRouteIntent(intent) as? Route.Cast
        val bvid = route?.bvid.orEmpty()
        val cid = route?.cid ?: 0L
        val startSec = route?.startSec ?: 0
        val quality = route?.quality?.takeIf { it > 0 } ?: settings.qualityPreference
        // 复用进程级会话，而不是每个扫描页 new 一个。
        val session = AppGraph.castSession
        setContent {
            val state by session.state.collectAsState()
            // 连上并开始播就回视频页；失败留在扫描页让用户看到原因。
            LaunchedEffect(state.status) {
                if (state.status == CastStatus.Playing) finish()
            }
            TeakActivityScaffold(
                settings = settings,
                onBack = { finish() },
                options = ActivityPageOptions(title = "投屏"),
            ) {
                CastScreenRoute(
                    repository = AppGraph.castDeviceRepository,
                    session = session,
                    onSelect = { device -> session.start(device, bvid, cid, startSec, quality) },
                )
            }
        }
    }
}
