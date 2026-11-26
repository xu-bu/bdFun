package com.aritxonly.bdfun.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aritxonly.bdfun.infra.playback.MediaPlaybackSession

class MediaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        com.aritxonly.bdfun.AppGraph.ensure(context.applicationContext)
        val session = com.aritxonly.bdfun.AppGraph.mediaControls.current ?: return
        when (intent?.action) {
            MediaPlaybackSession.ACTION_TOGGLE -> session.toggle()
            MediaPlaybackSession.ACTION_REWIND -> session.seekBy(-10_000)
            MediaPlaybackSession.ACTION_FORWARD -> session.seekBy(10_000)
            MediaPlaybackSession.ACTION_PREV -> session.previous()
            MediaPlaybackSession.ACTION_NEXT -> session.next()
            ACTION_PIP_TOGGLE -> session.toggle()
            ACTION_PIP_REWIND -> session.seekBy(-PIP_SEEK_MS)
            ACTION_PIP_FORWARD -> session.seekBy(PIP_SEEK_MS)
        }
    }
}
