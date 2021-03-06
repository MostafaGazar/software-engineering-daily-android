package com.koalatea.sedaily.downloadManager

import android.app.IntentService
import android.content.Intent
import android.os.Build

class DownloadIntentService: IntentService("SEDDownloadIntentService") {
    override fun onHandleIntent(intent: Intent?) {
        val url: String? = intent?.getStringExtra("episode-url")
        val id: String? = intent?.getStringExtra("episode-id")

        if (url == null || id == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(this.hashCode(), DownloadNotification.show())
        }

        val httpsurl = url.replace("http", "https")
        Downloader.downloadMp3(httpsurl, id)
    }
}