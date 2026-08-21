package com.bloklan.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bloklan.core.web.AdBlockWebViewClient
import com.bloklan.ui.theme.BgDark
import com.bloklan.ui.theme.CardBgDark
import com.bloklan.ui.theme.CardBorderDark
import com.bloklan.ui.theme.DangerRed
import com.bloklan.ui.theme.PrimaryNeon
import com.bloklan.ui.theme.SecondaryNeon
import com.bloklan.ui.theme.TextMuted
import com.bloklan.ui.theme.TextPrimary
import com.bloklan.ui.theme.TextSecondary

data class QuickBookmark(val title: String, val url: String, val icon: String = "🎬")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayerScreen() {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://m.youtube.com") }
    var inputUrlText by remember { mutableStateOf("https://m.youtube.com") }
    var isLoading by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val bookmarks = listOf(
        QuickBookmark("YouTube", "https://m.youtube.com", "▶️"),
        QuickBookmark("YouTube Music", "https://music.youtube.com", "🎵"),
        QuickBookmark("Twitch", "https://m.twitch.tv", "🟣"),
        QuickBookmark("Dailymotion", "https://www.dailymotion.com", "📺")
    )

    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top Bar: Navigation & URL input
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBgDark)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { webViewInstance?.goBack() },
                    modifier = Modifier.size(36.dp),
                    enabled = webViewInstance?.canGoBack() == true
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = if (webViewInstance?.canGoBack() == true) TextPrimary else TextMuted
                    )
                }

                IconButton(
                    onClick = { webViewInstance?.goForward() },
                    modifier = Modifier.size(36.dp),
                    enabled = webViewInstance?.canGoForward() == true
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (webViewInstance?.canGoForward() == true) TextPrimary else TextMuted
                    )
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = PrimaryNeon
                    )
                }

                // URL Search Bar
                OutlinedTextField(
                    value = inputUrlText,
                    onValueChange = { inputUrlText = it },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = BgDark,
                        unfocusedContainerColor = BgDark,
                        focusedBorderColor = PrimaryNeon,
                        unfocusedBorderColor = CardBorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secure",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            focusManager.clearFocus()
                            var target = inputUrlText.trim()
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                target = if (target.contains(".") && !target.contains(" ")) {
                                    "https://$target"
                                } else {
                                    "https://www.google.com/search?q=${target.replace(" ", "+")}"
                                }
                            }
                            currentUrl = target
                            inputUrlText = target
                            webViewInstance?.loadUrl(target)
                        }
                    )
                )

                IconButton(
                    onClick = {
                        isDesktopMode = !isDesktopMode
                        webViewInstance?.settings?.userAgentString = if (isDesktopMode) {
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        } else {
                            null
                        }
                        webViewInstance?.reload()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isDesktopMode) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = "Mode",
                        tint = if (isDesktopMode) SecondaryNeon else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Bookmark Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                bookmarks.forEach { bookmark ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgDark)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                            .clickable {
                                currentUrl = bookmark.url
                                inputUrlText = bookmark.url
                                webViewInstance?.loadUrl(bookmark.url)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = bookmark.icon, fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = bookmark.title,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Loading Progress Bar
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = PrimaryNeon,
                trackColor = CardBgDark
            )
        }

        // Embedded Ad-blocking WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = AdBlockWebViewClient(
                        onPageTitleChanged = { title, url ->
                            inputUrlText = url
                        },
                        onLoadingProgress = { loading ->
                            isLoading = loading
                        }
                    )

                    webChromeClient = WebChromeClient()

                    loadUrl(currentUrl)
                    webViewInstance = this
                }
            },
            update = {
                webViewInstance = it
            }
        )
    }
}
