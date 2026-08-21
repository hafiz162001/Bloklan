package com.bloklan

import com.bloklan.core.rules.RuleEngine
import com.bloklan.data.model.FilterCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleEngineTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
    }

    @Test
    fun testAdDomainBlocking() {
        // Exact match
        val (blocked1, rule1) = ruleEngine.isBlocked("doubleclick.net")
        assertTrue(blocked1)
        assertEquals("Iklan & Pelacak", rule1)

        // Subdomain match
        val (blocked2, rule2) = ruleEngine.isBlocked("securepubads.g.doubleclick.net")
        assertTrue(blocked2)
        assertEquals("Iklan & Pelacak", rule2)

        // Unity Ads match
        val (blocked3, _) = ruleEngine.isBlocked("auction.unityads.unity3d.com")
        assertTrue(blocked3)
    }

    @Test
    fun testSafeDomainAllowed() {
        val (blocked, _) = ruleEngine.isBlocked("github.com")
        assertFalse(blocked)

        val (blocked2, _) = ruleEngine.isBlocked("wikipedia.org")
        assertFalse(blocked2)
    }

    @Test
    fun testWhitelistOverride() {
        // Normally blocked
        val domain = "analytics.tiktok.com"
        val (blockedBefore, _) = ruleEngine.isBlocked(domain)
        assertTrue(blockedBefore)

        // Add to whitelist
        ruleEngine.addCustomWhitelist(domain)
        val (blockedAfter, rule) = ruleEngine.isBlocked(domain)
        assertFalse(blockedAfter)
        assertEquals("Whitelist", rule)

        // Remove from whitelist
        ruleEngine.removeCustomWhitelist(domain)
        val (blockedRestored, _) = ruleEngine.isBlocked(domain)
        assertTrue(blockedRestored)
    }

    @Test
    fun testCustomBlacklist() {
        val customDomain = "bad-spam-website.xyz"
        val (blockedBefore, _) = ruleEngine.isBlocked(customDomain)
        assertFalse(blockedBefore)

        ruleEngine.addCustomBlacklist(customDomain)
        val (blockedAfter, rule) = ruleEngine.isBlocked(customDomain)
        assertTrue(blockedAfter)
        assertEquals("Custom Blacklist", rule)

        // Subdomain of blacklist
        val (subBlocked, _) = ruleEngine.isBlocked("sub.bad-spam-website.xyz")
        assertTrue(subBlocked)
    }

    @Test
    fun testDisableCategory() {
        val socialDomain = "pixel.facebook.com"
        val (blocked1, _) = ruleEngine.isBlocked(socialDomain)
        assertTrue(blocked1)

        // Disable Social Trackers
        ruleEngine.setCategoryEnabled(FilterCategory.SOCIAL_TRACKERS, false)
        val (blocked2, _) = ruleEngine.isBlocked(socialDomain)
        assertFalse(blocked2)

        // Re-enable
        ruleEngine.setCategoryEnabled(FilterCategory.SOCIAL_TRACKERS, true)
        val (blocked3, _) = ruleEngine.isBlocked(socialDomain)
        assertTrue(blocked3)
    }
}
