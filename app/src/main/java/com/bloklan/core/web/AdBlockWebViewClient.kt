package com.bloklan.core.web

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bloklan.data.repository.AppRepository
import java.io.ByteArrayInputStream

class AdBlockWebViewClient(
    private val onPageTitleChanged: ((String, String) -> Unit)? = null,
    private val onLoadingProgress: ((Boolean) -> Unit)? = null
) : WebViewClient() {

    private val repository = AppRepository.instance

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        val host = request.url.host ?: ""

        // 1. Check if domain is blocked by RuleEngine
        if (host.isNotEmpty()) {
            val (isBlocked, _) = repository.ruleEngine.isBlocked(host)
            if (isBlocked) {
                return createEmptyResponse()
            }
        }

        // 2. Specific YouTube ad and tracking endpoints
        val path = request.url.path ?: ""
        if (path.contains("/pagead/") ||
            path.contains("/api/stats/ads") ||
            path.contains("/api/stats/atr") ||
            path.contains("/api/stats/playback") ||
            path.contains("/ptracking") ||
            path.contains("/youtubei/v1/log_event") ||
            url.contains("doubleclick.net") ||
            url.contains("googleads") ||
            url.contains("adservice.google") ||
            url.contains("static.doubleclick.net")
        ) {
            return createEmptyResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers" to "*"
            ),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onLoadingProgress?.invoke(true)
        injectAdBlockScripts(view)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        injectAdBlockScripts(view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onLoadingProgress?.invoke(false)
        injectAdBlockScripts(view)
        if (view != null && url != null) {
            onPageTitleChanged?.invoke(view.title ?: "", url)
        }
    }

    private fun injectAdBlockScripts(view: WebView?) {
        view?.evaluateJavascript(AdBlockScripts.JS_YOUTUBE_AD_SKIPPER, null)
    }
}
