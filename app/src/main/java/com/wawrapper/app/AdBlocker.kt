package com.wawrapper.app

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {

    private val allowedSuffixes = listOf("whatsapp.com", "whatsapp.net")

    private val trackerHosts = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "googlesyndication.com",
        "googleadservices.com",
        "doubleclick.net",
        "adservice.google.com",
        "connect.facebook.net",
        "graph.facebook.com",
        "sentry.io",
        "ingest.sentry.io",
        "nr-data.net",
        "newrelic.com",
        "hotjar.com",
        "hotjar.io",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "scorecardresearch.com",
        "quantserve.com",
        "crashlytics.com",
        "moatads.com",
        "ads.yahoo.com",
        "bat.bing.com",
        "clarity.ms",
        "taboola.com",
        "outbrain.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "smartadserver.com",
        "bidswitch.net",
        "sharethrough.com",
        "33across.com",
        "yieldmo.com",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net"
    )

    private val mediaHosts = setOf(
        "mmg.whatsapp.net",
        "pps.whatsapp.net"
    )

    fun shouldBlock(url: String, blockTrackers: Boolean, liteMode: Boolean): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false

        if (allowedSuffixes.any { host == it || host.endsWith(".$it") }) {
            return liteMode && mediaHosts.any { host == it || host.endsWith(".$it") }
        }

        return blockTrackers && trackerHosts.any { host == it || host.endsWith(".$it") }
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
