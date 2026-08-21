package com.bloklan.data.model

data class DnsQueryLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val domain: String,
    val queryType: String = "A",
    val isBlocked: Boolean,
    val matchedRule: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val latencyMs: Long = 0L,
    val clientIp: String = "10.10.10.2",
    val upstreamServer: String = "1.1.1.1"
)

data class VpnStats(
    val totalQueries: Long = 0L,
    val blockedQueries: Long = 0L,
    val allowedQueries: Long = 0L,
    val startTime: Long = 0L,
    val bytesProcessed: Long = 0L
) {
    val blockPercentage: Int
        get() = if (totalQueries > 0) ((blockedQueries.toDouble() / totalQueries) * 100).toInt() else 0
}

data class DnsServerConfig(
    val id: String,
    val name: String,
    val primaryIp: String,
    val secondaryIp: String,
    val description: String,
    val isCustom: Boolean = false
)

enum class FilterCategory(val displayName: String, val description: String, val countEstimate: Int) {
    ADS_AND_TRACKERS("Iklan & Pelacak", "Memblokir iklan mobile, banner, video ads, dan popup", 4200),
    ANALYTICS_TELEMETRY("Telemetri & Analitik", "Mencegah pelacakan perilaku pengguna oleh OEM & SDK", 1500),
    SOCIAL_TRACKERS("Pelacak Media Sosial", "Memblokir widget dan pelacak Facebook, TikTok, dll.", 850),
    MALWARE_PHISHING("Malware & Phishing", "Melindungi dari domain berbahaya dan penipuan", 2100)
}

data class BypassAppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isBypassed: Boolean = false
)

