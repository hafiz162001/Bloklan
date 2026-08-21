package com.bloklan.core.web

object AdBlockScripts {

    /**
     * Injected CSS to hide ad banners, yellow timeline ad ticks, promos, overlays, and sponsor slots on YouTube and web video platforms.
     */
    val CSS_INJECTION = """
        /* Hide YouTube Ad Elements & Yellow Timeline Ticks */
        .ytp-ad-overlay-container,
        .ytp-ad-message-container,
        .ytp-ad-progress,
        .ytp-ad-progress-list,
        .ytp-ad-hover-text-container,
        .ytp-ad-preview-container,
        .ytp-ad-text,
        .ytp-ad-module,
        .video-ads,
        .ytp-ad-player-overlay,
        ytm-player-ad-ui,
        ytm-companion-ad-renderer,
        ytd-ad-slot-renderer,
        ytd-in-feed-ad-layout-renderer,
        ytd-banner-promo-renderer,
        ytd-promoted-sparkles-web-renderer,
        ytd-promoted-video-renderer,
        ytd-compact-promoted-video-renderer,
        ytm-promoted-sparkles-web-renderer,
        #player-ads,
        #offer-module,
        .ad-container,
        .ad-showing .html5-video-player,
        .sparkles-light-cta,
        yt-mealbar-promo-renderer,
        #premium-yva,
        .masthead-ad-control,
        [id*="ad-slot"],
        [class*="ad-unit"],
        [class*="ad-container"] {
            display: none !important;
            visibility: hidden !important;
            opacity: 0 !important;
            height: 0 !important;
            pointer-events: none !important;
        }
    """.trimIndent()

    /**
     * Powerful JavaScript injector that:
     * 1. Intercepts YouTube JSON API responses (removes adPlacements, playerAds, adSlots).
     * 2. Auto skips unskippable yellow ads instantly (16x speed + currentTime jump + click skip).
     * 3. Uses MutationObserver for instant trigger without lag.
     */
    val JS_YOUTUBE_AD_SKIPPER = """
        (function() {
            if (window.__bloklan_ad_skipper_injected) return;
            window.__bloklan_ad_skipper_injected = true;

            // 1. Intercept JSON API calls for YouTube Player
            try {
                const originalFetch = window.fetch;
                window.fetch = async function(...args) {
                    const url = args[0] ? (typeof args[0] === 'string' ? args[0] : args[0].url || '') : '';
                    
                    // Block ad tracking requests outright
                    if (url.includes('/api/stats/ads') || 
                        url.includes('/pagead/') || 
                        url.includes('doubleclick.net') || 
                        url.includes('/ptracking')) {
                        return new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } });
                    }

                    const response = await originalFetch.apply(this, args);

                    // Clean YouTube player payload
                    if (url.includes('/youtubei/v1/player') || url.includes('/youtubei/v1/next')) {
                        try {
                            const clone = response.clone();
                            const data = await clone.json();
                            if (data) {
                                if (data.adPlacements) delete data.adPlacements;
                                if (data.playerAds) delete data.playerAds;
                                if (data.adSlots) delete data.adSlots;
                                if (data.adBreakHeartbeatParams) delete data.adBreakHeartbeatParams;
                                return new Response(JSON.stringify(data), {
                                    headers: response.headers,
                                    status: response.status,
                                    statusText: response.statusText
                                });
                            }
                        } catch(e) {}
                    }
                    return response;
                };
            } catch(e) {}

            // 2. Continuous Ad Detection & Auto-Skipper
            function handleAds() {
                // Find all video elements on page
                const videos = document.querySelectorAll('video');
                const isAdShowing = document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, ytm-player-ad-ui, [class*="ytp-ad-"], ytm-promoted-sparkles-web-renderer');

                if (isAdShowing) {
                    videos.forEach(video => {
                        if (video) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (video.duration && !isNaN(video.duration) && isFinite(video.duration)) {
                                video.currentTime = video.duration;
                            } else {
                                video.currentTime = 99999;
                            }
                            video.dispatchEvent(new Event('ended'));
                        }
                    });
                }

                // Click all skip button variants
                const skipButtonSelectors = [
                    '.ytp-ad-skip-button',
                    '.ytp-ad-skip-button-modern',
                    '.ytp-skip-ad-button',
                    '.ytp-ad-skip-button-slot',
                    'button[id*="skip-button"]',
                    '.videoAdUiSkipButton',
                    '.ytp-ad-overlay-close-button',
                    '.ytm-ad-skip-button',
                    '[aria-label*="Skip ad"]',
                    '[aria-label*="Lewati iklan"]'
                ];

                for (const selector of skipButtonSelectors) {
                    const buttons = document.querySelectorAll(selector);
                    buttons.forEach(btn => {
                        if (btn && typeof btn.click === 'function') {
                            btn.click();
                        }
                    });
                }

                // Dismiss promo popups
                const dismissButtons = document.querySelectorAll('yt-button-renderer[dialog-dismiss], #dismiss-button, ytm-mealbar-promo-renderer button');
                dismissButtons.forEach(btn => {
                    if (btn && typeof btn.click === 'function') {
                        btn.click();
                    }
                });
            }

            // High frequency interval (50ms) for instantaneous response
            setInterval(handleAds, 50);

            // MutationObserver to trigger instantly on DOM changes
            try {
                const observer = new MutationObserver(() => {
                    handleAds();
                });
                observer.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true
                });
            } catch(e) {}

            // Inject CSS Rules
            function injectStyles() {
                const style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = `${CSS_INJECTION.replace("`", "\\`")}`;
                if (document.head) {
                    document.head.appendChild(style);
                } else if (document.body) {
                    document.body.appendChild(style);
                }
            }

            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', injectStyles);
            } else {
                injectStyles();
            }
        })();
    """.trimIndent()
}
