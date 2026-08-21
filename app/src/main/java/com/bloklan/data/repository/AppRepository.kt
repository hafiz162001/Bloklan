package com.bloklan.data.repository

import com.bloklan.core.rules.DefaultRules
import com.bloklan.core.rules.RuleEngine
import com.bloklan.data.model.DnsQueryLog
import com.bloklan.data.model.DnsServerConfig
import com.bloklan.data.model.FilterCategory
import com.bloklan.data.model.VpnStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.LinkedList

class AppRepository private constructor() {

    val ruleEngine = RuleEngine()

    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()

    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    private val maxLogs = 200
    private val rawLogs = LinkedList<DnsQueryLog>()
    private val _queryLogs = MutableStateFlow<List<DnsQueryLog>>(emptyList())
    val queryLogs: StateFlow<List<DnsQueryLog>> = _queryLogs.asStateFlow()

    private val _selectedDns = MutableStateFlow(DefaultRules.DNS_PRESETS.first())
    val selectedDns: StateFlow<DnsServerConfig> = _selectedDns.asStateFlow()

    private val _activeCategories = MutableStateFlow(ruleEngine.getActiveCategories())
    val activeCategories: StateFlow<Set<FilterCategory>> = _activeCategories.asStateFlow()

    private val _customWhitelist = MutableStateFlow(ruleEngine.getCustomWhitelist())
    val customWhitelist: StateFlow<Set<String>> = _customWhitelist.asStateFlow()

    private val _customBlacklist = MutableStateFlow(ruleEngine.getCustomBlacklist())
    val customBlacklist: StateFlow<Set<String>> = _customBlacklist.asStateFlow()

    fun setVpnActive(active: Boolean) {
        _isVpnActive.value = active
        if (active && _stats.value.startTime == 0L) {
            _stats.update { it.copy(startTime = System.currentTimeMillis()) }
        }
    }

    fun recordQuery(log: DnsQueryLog, bytes: Long = 0L) {
        synchronized(rawLogs) {
            rawLogs.addFirst(log)
            if (rawLogs.size > maxLogs) {
                rawLogs.removeLast()
            }
            _queryLogs.value = ArrayList(rawLogs)
        }

        _stats.update { current ->
            current.copy(
                totalQueries = current.totalQueries + 1,
                blockedQueries = current.blockedQueries + if (log.isBlocked) 1 else 0,
                allowedQueries = current.allowedQueries + if (!log.isBlocked) 1 else 0,
                bytesProcessed = current.bytesProcessed + bytes
            )
        }
    }

    fun setFilterCategoryEnabled(category: FilterCategory, enabled: Boolean) {
        ruleEngine.setCategoryEnabled(category, enabled)
        _activeCategories.value = ruleEngine.getActiveCategories()
    }

    fun addToWhitelist(domain: String) {
        ruleEngine.addCustomWhitelist(domain)
        _customWhitelist.value = ruleEngine.getCustomWhitelist()
    }

    fun removeFromWhitelist(domain: String) {
        ruleEngine.removeCustomWhitelist(domain)
        _customWhitelist.value = ruleEngine.getCustomWhitelist()
    }

    fun addToBlacklist(domain: String) {
        ruleEngine.addCustomBlacklist(domain)
        _customBlacklist.value = ruleEngine.getCustomBlacklist()
    }

    fun removeFromBlacklist(domain: String) {
        ruleEngine.removeCustomBlacklist(domain)
        _customBlacklist.value = ruleEngine.getCustomBlacklist()
    }

    fun setUpstreamDns(server: DnsServerConfig) {
        _selectedDns.value = server
    }

    fun clearLogs() {
        synchronized(rawLogs) {
            rawLogs.clear()
            _queryLogs.value = emptyList()
        }
    }

    fun resetStats() {
        _stats.value = VpnStats(startTime = if (_isVpnActive.value) System.currentTimeMillis() else 0L)
    }

    companion object {
        val instance: AppRepository by lazy { AppRepository() }
    }
}
