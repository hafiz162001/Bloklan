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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloklan.data.model.DnsQueryLog
import com.bloklan.data.repository.AppRepository
import com.bloklan.ui.theme.BgDark
import com.bloklan.ui.theme.CardBgDark
import com.bloklan.ui.theme.CardBorderDark
import com.bloklan.ui.theme.DangerRed
import com.bloklan.ui.theme.DangerRedBg
import com.bloklan.ui.theme.PrimaryNeon
import com.bloklan.ui.theme.SecondaryNeon
import com.bloklan.ui.theme.TextMuted
import com.bloklan.ui.theme.TextPrimary
import com.bloklan.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogFilter { ALL, BLOCKED, ALLOWED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    val repository = AppRepository.instance
    val logs by repository.queryLogs.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLogForAction by remember { mutableStateOf<DnsQueryLog?>(null) }

    val filteredLogs = logs.filter { log ->
        val matchesFilter = when (selectedFilter) {
            LogFilter.ALL -> true
            LogFilter.BLOCKED -> log.isBlocked
            LogFilter.ALLOWED -> !log.isBlocked
        }
        val matchesSearch = searchQuery.isBlank() || log.domain.contains(searchQuery.trim(), ignoreCase = true)
        matchesFilter && matchesSearch
    }

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
                    text = "Live DNS Monitor",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredLogs.size} kueri tercatat",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = { repository.clearLogs() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CardBgDark)
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Hapus Log",
                    tint = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari domain...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardBgDark,
                unfocusedContainerColor = CardBgDark,
                focusedBorderColor = PrimaryNeon,
                unfocusedBorderColor = CardBorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = selectedFilter == LogFilter.ALL,
                onClick = { selectedFilter = LogFilter.ALL },
                label = { Text("Semua (${logs.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryNeon,
                    selectedLabelColor = BgDark,
                    containerColor = CardBgDark,
                    labelColor = TextSecondary
                )
            )
            FilterChip(
                selected = selectedFilter == LogFilter.BLOCKED,
                onClick = { selectedFilter = LogFilter.BLOCKED },
                label = { Text("Diblokir (${logs.count { it.isBlocked }})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DangerRed,
                    selectedLabelColor = TextPrimary,
                    containerColor = CardBgDark,
                    labelColor = DangerRed
                )
            )
            FilterChip(
                selected = selectedFilter == LogFilter.ALLOWED,
                onClick = { selectedFilter = LogFilter.ALLOWED },
                label = { Text("Diizinkan (${logs.count { !it.isBlocked }})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SecondaryNeon,
                    selectedLabelColor = BgDark,
                    containerColor = CardBgDark,
                    labelColor = TextSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logs List
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "Tidak ada domain yang cocok" else "Belum ada catatan aktivitas DNS",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLogs, key = { it.id }) { log ->
                    LogCard(
                        log = log,
                        onClick = { selectedLogForAction = log }
                    )
                }
            }
        }
    }

    // Detail & Action Dialog
    selectedLogForAction?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogForAction = null },
            containerColor = CardBgDark,
            title = {
                Text(
                    text = "Detail Kueri DNS",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Domain:", color = TextMuted, fontSize = 12.sp)
                    Text(text = log.domain, color = PrimaryNeon, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Status: ${if (log.isBlocked) "DIBLOKIR (${log.matchedRule ?: "Rule Engine"})" else "DIIZINKAN"}", color = if (log.isBlocked) DangerRed else SecondaryNeon, fontSize = 13.sp)
                    Text(text = "Tipe Kueri: ${log.queryType}", color = TextSecondary, fontSize = 13.sp)
                    Text(text = "Latency: ${log.latencyMs} ms", color = TextSecondary, fontSize = 13.sp)
                    Text(text = "Server: ${log.upstreamServer}", color = TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(log.domain))
                        Toast.makeText(context, "Domain disalin", Toast.LENGTH_SHORT).show()
                        selectedLogForAction = null
                    }) {
                        Text("Salin", color = SecondaryNeon)
                    }

                    if (log.isBlocked) {
                        Button(
                            onClick = {
                                repository.addToWhitelist(log.domain)
                                Toast.makeText(context, "${log.domain} ditambahkan ke Whitelist", Toast.LENGTH_SHORT).show()
                                selectedLogForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryNeon)
                        ) {
                            Text("Izinkan (Whitelist)", color = BgDark)
                        }
                    } else {
                        Button(
                            onClick = {
                                repository.addToBlacklist(log.domain)
                                Toast.makeText(context, "${log.domain} ditambahkan ke Blacklist", Toast.LENGTH_SHORT).show()
                                selectedLogForAction = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                        ) {
                            Text("Blokir Domain", color = TextPrimary)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogForAction = null }) {
                    Text("Tutup", color = TextMuted)
                }
            }
        )
    }
}

@Composable
fun LogCard(
    log: DnsQueryLog,
    onClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val timeStr = timeFormat.format(Date(log.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isBlocked) DangerRedBg else CardBgDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (log.isBlocked) DangerRed.copy(alpha = 0.35f) else CardBorderDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (log.isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (log.isBlocked) DangerRed else PrimaryNeon,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = log.domain,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${log.queryType} • ${log.latencyMs}ms • $timeStr",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (log.isBlocked) DangerRed.copy(alpha = 0.2f) else PrimaryNeon.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (log.isBlocked) "BLOCKED" else "PASS",
                        color = if (log.isBlocked) DangerRed else PrimaryNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (log.isBlocked && log.matchedRule != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.matchedRule,
                        color = DangerRed.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
