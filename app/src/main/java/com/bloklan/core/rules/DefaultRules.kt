package com.bloklan.core.rules

import com.bloklan.data.model.FilterCategory

object DefaultRules {

    val DNS_PRESETS = listOf(
        com.bloklan.data.model.DnsServerConfig(
            id = "cloudflare",
            name = "Cloudflare DNS",
            primaryIp = "1.1.1.1",
            secondaryIp = "1.0.0.1",
            description = "DNS Publik Cepat & Privasi Tinggi"
        ),
        com.bloklan.data.model.DnsServerConfig(
            id = "google",
            name = "Google Public DNS",
            primaryIp = "8.8.8.8",
            secondaryIp = "8.8.4.4",
            description = "DNS Global dengan performa stabil"
        ),
        com.bloklan.data.model.DnsServerConfig(
            id = "quad9",
            name = "Quad9 Security DNS",
            primaryIp = "9.9.9.9",
            secondaryIp = "149.112.112.112",
            description = "Perlindungan otomatis dari ancaman siber"
        ),
        com.bloklan.data.model.DnsServerConfig(
            id = "adguard",
            name = "AdGuard DNS (Default)",
            primaryIp = "94.140.14.14",
            secondaryIp = "94.140.15.15",
            description = "Lapisan pemblokir iklan tambahan di hulu"
        )
    )

    val ADS_TRACKERS_DOMAINS = setOf(
        // Google & DoubleClick Ads
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adservice.google.co.id",
        "googleadservices.com",
        "www.googleadservices.com",
        "ads.google.com",
        "admob.com",
        "media.admob.com",
        "fundingchoicesmessages.google.com",

        // Unity Ads
        "unityads.unity3d.com",
        "auction.unityads.unity3d.com",
        "config.unityads.unity3d.com",
        "webview.unityads.unity3d.com",
        "cdp.cloud.unity3d.com",

        // AppLovin
        "applovin.com",
        "applvn.com",
        "d.applovin.com",
        "a.applovin.com",
        "assets.applovin.com",
        "rt.applovin.com",

        // IronSource & Supersonic
        "supersonicads.com",
        "ironsrc.com",
        "is.com",
        "track.ironsrc.com",
        "init.supersonicads.com",

        // Vungle & Liftoff
        "vungle.com",
        "ads.vungle.com",
        "api.vungle.com",
        "cdn.vungle.com",
        "liftoff.io",

        // Mintegral
        "mintegral.com",
        "adservice.mintegral.com",
        "analytics.mintegral.com",
        "pglstatp-toutiao.com",

        // ByteDance / Pangle / TikTok Ads
        "pangolin-sdk-toutiao.com",
        "pangolin.snssdk.com",
        "ad.byteoversea.com",
        "analytics.tiktok.com",
        "ads.tiktok.com",
        "toblog.ctobsnssdk.com",

        // InMobi
        "inmobi.com",
        "config.inmobi.com",
        "tracker.inmobi.com",
        "i.l.inmobicdn.net",

        // Chartboost
        "chartboost.com",
        "live.chartboost.com",
        "da.chartboost.com",

        // Facebook / Meta Audience Network
        "an.facebook.com",
        "graph.facebook.com/ads",
        "connect.facebook.net/en_US/fbevents.js",

        // Amazon Ads & MoPub
        "aax.amazon-adsystem.com",
        "c.amazon-adsystem.com",
        "mopub.com",
        "ads.mopub.com",

        // Criteo & OpenX & Taboola & Outbrain
        "criteo.com",
        "static.criteo.net",
        "openx.net",
        "taboola.com",
        "outbrain.com",
        "widgets.outbrain.com",

        // Smaato & PubMatic & Rubicon
        "smaato.net",
        "pubmatic.com",
        "rubiconproject.com",

        // Mobile Ad Popups & Push Ads
        "propellerads.com",
        "popads.net",
        "adsterra.com",
        "exoclick.com",
        "mgid.com",
        "revcontent.com"
    )

    val ANALYTICS_DOMAINS = setOf(
        // Google Analytics & Firebase
        "google-analytics.com",
        "ssl.google-analytics.com",
        "analytics.google.com",
        "app-measurement.com",
        "firebase-settings.crashlytics.com",
        "crashlytics.com",
        "reports.crashlytics.com",

        // Adjust & AppsFlyer & Branch
        "app.adjust.com",
        "adjust.com",
        "app.appsflyer.com",
        "appsflyer.com",
        "api2.branch.io",
        "branch.io",
        "api.singular.net",
        "singular.net",
        "kochava.com",

        // Mixpanel, Amplitude, Flurry
        "api.mixpanel.com",
        "mixpanel.com",
        "api.amplitude.com",
        "amplitude.com",
        "data.flurry.com",
        "flurry.com",

        // OEM Telemetry (Xiaomi, Oppo, Vivo, Samsung, Huawei)
        "data.mistat.xiaomi.com",
        "tracking.miui.com",
        "api.ad.xiaomi.com",
        "ad.mi.com",
        "log.ad.xiaomi.com",
        "stat.pandora.xiaomi.com",
        "metrics.data.hicloud.com",
        "logservice.hicloud.com",
        "logservice1.hicloud.com",
        "samsungadhub.com",
        "samsungimaging.com/log",
        "data.samsungads.com",
        "ads.samsung.com"
    )

    val SOCIAL_TRACKERS_DOMAINS = setOf(
        "connect.facebook.net",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "static.ads-twitter.com",
        "t.co",
        "ads.pinterest.com",
        "analytics.pinterest.com",
        "ads.linkedin.com",
        "snap.licdn.com",
        "tr.snapchat.com",
        "sc-static.net"
    )

    val MALWARE_PHISHING_DOMAINS = setOf(
        "trackvoluum.com",
        "zeroredirect1.com",
        "trafficjunky.com",
        "clickadu.com",
        "onclicktop.com",
        "adreactor.com",
        "trafficforce.com",
        "wigetmedia.com",
        "top10bestantivirus.com",
        "warning-cleaner.com",
        "phone-virus-scan.com"
    )

    fun getDomainsForCategory(category: FilterCategory): Set<String> {
        return when (category) {
            FilterCategory.ADS_AND_TRACKERS -> ADS_TRACKERS_DOMAINS
            FilterCategory.ANALYTICS_TELEMETRY -> ANALYTICS_DOMAINS
            FilterCategory.SOCIAL_TRACKERS -> SOCIAL_TRACKERS_DOMAINS
            FilterCategory.MALWARE_PHISHING -> MALWARE_PHISHING_DOMAINS
        }
    }
}
