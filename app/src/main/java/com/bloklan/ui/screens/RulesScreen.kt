package com.bloklan.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloklan.data.model.FilterCategory
import com.bloklan.data.repository.AppRepository
import com.bloklan.ui.theme.BgDark
import com.bloklan.ui.theme.CardBgDark
import com.bloklan.ui.theme.CardBorderDark
import com.bloklan.ui.theme.DangerRed
import com.bloklan.ui.theme.PrimaryNeon
import com.bloklan.ui.theme.SecondaryNeon
import com.bloklan.ui.theme.TextMuted
import com.bloklan.ui.theme.TextPrimary
import com.bloklan.ui.theme.TextSecondary

@Composable
fun RulesScreen() {
    val repository = AppRepository.instance
    val activeCategories by repository.activeCategories.collectAsState()
    val customWhitelist by repository.customWhitelist.collectAsState()
    val customBlacklist by repository.customBlacklist.collectAsState()
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showAddDomainDialog by remember { mutableStateOf(false) }
    var isAddingToWhitelist by remember { mutableStateOf(true) }
    var newDomainInput by remember { mutableStateOf("") }

    val tabs = listOf("Daftar Filter", "Whitelist (${customWhitelist.size})", "Blacklist (${customBlacklist.size})")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Aturan & Filter",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${repository.ruleEngine.getTotalRulesCount()} aturan aktif",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (selectedTabIndex > 0) {
                IconButton(
                    onClick = {
                        isAddingToWhitelist = (selectedTabIndex == 1)
                        newDomainInput = ""
                        showAddDomainDialog = true
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PrimaryNeon)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Domain",
                        tint = BgDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = CardBgDark,
            contentColor = PrimaryNeon,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = PrimaryNeon
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CardBorderDark, RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTabIndex == index) PrimaryNeon else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabIndex) {
            0 -> {
                // Categories List
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    FilterCategory.values().forEach { category ->
                        CategoryCard(
                            category = category,
                            isEnabled = activeCategories.contains(category),
                            onToggle = { enabled ->
                                repository.setFilterCategoryEnabled(category, enabled)
                            }
                        )
                    }
                }
            }
            1 -> {
                // Whitelist
                CustomDomainList(
                    domains = customWhitelist.toList(),
                    emptyMessage = "Belum ada domain di Whitelist.\nDomain di Whitelist tidak akan pernah diblokir.",
                    isWhitelist = true,
                    onDelete = { domain ->
                        repository.removeFromWhitelist(domain)
                        Toast.makeText(context, "$domain dihapus dari Whitelist", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            2 -> {
                // Blacklist
                CustomDomainList(
                    domains = customBlacklist.toList(),
                    emptyMessage = "Belum ada domain di Blacklist.\nTambahkan domain kustom yang selalu ingin diblokir.",
                    isWhitelist = false,
                    onDelete = { domain ->
                        repository.removeFromBlacklist(domain)
                        Toast.makeText(context, "$domain dihapus dari Blacklist", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Add Domain Dialog
    if (showAddDomainDialog) {
        AlertDialog(
            onDismissRequest = { showAddDomainDialog = false },
            containerColor = CardBgDark,
            title = {
                Text(
                    text = if (isAddingToWhitelist) "Tambah ke Whitelist" else "Tambah ke Blacklist",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isAddingToWhitelist)
                            "Masukkan domain yang selalu diizinkan (contoh: example.com):"
                        else
                            "Masukkan domain yang selalu diblokir (contoh: ads.custom.com):",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = newDomainInput,
                        onValueChange = { newDomainInput = it.trim().lowercase() },
                        placeholder = { Text("domain.com", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgDark,
                            unfocusedContainerColor = BgDark,
                            focusedBorderColor = PrimaryNeon,
                            unfocusedBorderColor = CardBorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val domain = newDomainInput.trim().trimEnd('.')
                        if (domain.contains('.')) {
                            if (isAddingToWhitelist) {
                                repository.addToWhitelist(domain)
                                Toast.makeText(context, "$domain ditambahkan ke Whitelist", Toast.LENGTH_SHORT).show()
                            } else {
                                repository.addToBlacklist(domain)
                                Toast.makeText(context, "$domain ditambahkan ke Blacklist", Toast.LENGTH_SHORT).show()
                            }
                            showAddDomainDialog = false
                        } else {
                            Toast.makeText(context, "Format domain tidak valid", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                ) {
                    Text("Simpan", color = BgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDomainDialog = false }) {
                    Text("Batal", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun CategoryCard(
    category: FilterCategory,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val icon = when (category) {
        FilterCategory.ADS_AND_TRACKERS -> Icons.Default.Block
        FilterCategory.ANALYTICS_TELEMETRY -> Icons.Default.Analytics
        FilterCategory.SOCIAL_TRACKERS -> Icons.Default.Share
        FilterCategory.MALWARE_PHISHING -> Icons.Default.Security
    }

    val accentColor = when (category) {
        FilterCategory.ADS_AND_TRACKERS -> DangerRed
        FilterCategory.ANALYTICS_TELEMETRY -> SecondaryNeon
        FilterCategory.SOCIAL_TRACKERS -> Color(0xFF818CF8)
        FilterCategory.MALWARE_PHISHING -> PrimaryNeon
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBgDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.displayName,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = category.displayName,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.description,
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "~${category.countEstimate} domain filter",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BgDark,
                    checkedTrackColor = PrimaryNeon,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = BgDark
                )
            )
        }
    }
}

@Composable
fun CustomDomainList(
    domains: List<String>,
    emptyMessage: String,
    isWhitelist: Boolean,
    onDelete: (String) -> Unit
) {
    if (domains.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(domains) { domain ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBgDark),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isWhitelist) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (isWhitelist) PrimaryNeon else DangerRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = domain,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = { onDelete(domain) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Hapus",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
