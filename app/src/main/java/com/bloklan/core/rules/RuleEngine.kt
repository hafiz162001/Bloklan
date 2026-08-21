package com.bloklan.core.rules

import com.bloklan.data.model.FilterCategory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class RuleEngine {

    private val activeCategories = CopyOnWriteArraySet<FilterCategory>().apply {
        addAll(FilterCategory.values())
    }

    private val categoryDomainSets = ConcurrentHashMap<FilterCategory, Set<String>>()
    private val customBlacklist = CopyOnWriteArraySet<String>()
    private val customWhitelist = CopyOnWriteArraySet<String>()

    init {
        // Initialize default rules
        for (category in FilterCategory.values()) {
            categoryDomainSets[category] = DefaultRules.getDomainsForCategory(category)
        }
    }

    fun isBlocked(domain: String): Pair<Boolean, String?> {
        val cleanDomain = normalizeDomain(domain)

        // 1. Check Whitelist first
        if (isDomainInSet(cleanDomain, customWhitelist)) {
            return Pair(false, "Whitelist")
        }

        // 2. Check Custom Blacklist
        if (isDomainInSet(cleanDomain, customBlacklist)) {
            return Pair(true, "Custom Blacklist")
        }

        // 3. Check Active Filter Categories
        for (category in activeCategories) {
            val domains = categoryDomainSets[category] ?: continue
            if (isDomainInSet(cleanDomain, domains)) {
                return Pair(true, category.displayName)
            }
        }

        return Pair(false, null)
    }

    private fun isDomainInSet(domain: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        if (set.contains(domain)) return true

        // Check parent domains (e.g., "ad.mobile.googleads.com" -> checks "mobile.googleads.com", "googleads.com")
        var sub = domain
        while (true) {
            val dotIndex = sub.indexOf('.')
            if (dotIndex == -1 || dotIndex == sub.length - 1) break
            sub = sub.substring(dotIndex + 1)
            if (set.contains(sub)) {
                return true
            }
        }
        return false
    }

    private fun normalizeDomain(domain: String): String {
        return domain.trim().trimEnd('.').lowercase()
    }

    fun setCategoryEnabled(category: FilterCategory, enabled: Boolean) {
        if (enabled) {
            activeCategories.add(category)
        } else {
            activeCategories.remove(category)
        }
    }

    fun isCategoryEnabled(category: FilterCategory): Boolean {
        return activeCategories.contains(category)
    }

    fun addCustomBlacklist(domain: String) {
        customBlacklist.add(normalizeDomain(domain))
    }

    fun removeCustomBlacklist(domain: String) {
        customBlacklist.remove(normalizeDomain(domain))
    }

    fun addCustomWhitelist(domain: String) {
        customWhitelist.add(normalizeDomain(domain))
    }

    fun removeCustomWhitelist(domain: String) {
        customWhitelist.remove(normalizeDomain(domain))
    }

    fun getCustomBlacklist(): Set<String> = customBlacklist.toSet()
    fun getCustomWhitelist(): Set<String> = customWhitelist.toSet()
    fun getActiveCategories(): Set<FilterCategory> = activeCategories.toSet()

    fun getTotalRulesCount(): Int {
        var count = customBlacklist.size
        for (category in activeCategories) {
            count += categoryDomainSets[category]?.size ?: 0
        }
        return count
    }
}
