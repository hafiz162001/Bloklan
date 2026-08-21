package com.bloklan.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.bloklan.MainActivity
import com.bloklan.R
import com.bloklan.core.dns.DnsPacketParser
import com.bloklan.core.dns.DnsRelay
import com.bloklan.data.model.DnsQueryLog
import com.bloklan.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class BloklanVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var vpnJob: Job? = null
    private lateinit var dnsRelay: DnsRelay
    private val repository = AppRepository.instance

    companion object {
        const val ACTION_START = "com.bloklan.START_VPN"
        const val ACTION_STOP = "com.bloklan.STOP_VPN"
        private const val NOTIFICATION_CHANNEL_ID = "bloklan_vpn_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_DNS_IP = "10.10.10.1"
        private const val VPN_INTERFACE_IP = "10.10.10.2"
    }

    override fun onCreate() {
        super.onCreate()
        dnsRelay = DnsRelay(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpn()
                return START_STICKY
            }
        }
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            val builder = Builder()
                .setSession("Bloklan Ad Blocker")
                .setMtu(1500)
                .addAddress(VPN_INTERFACE_IP, 32)
                .addDnsServer(VPN_DNS_IP)
                .addRoute(VPN_DNS_IP, 32)

            // Disallow our own app from VPN loopback
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Disallow user-selected bypass apps (Split Tunneling)
            val excludedApps = repository.excludedPackages.value
            for (pkg in excludedApps) {
                if (pkg != packageName) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopVpn()
                return
            }

            repository.setVpnActive(true)

            serviceScope = CoroutineScope(Dispatchers.IO)
            vpnJob = serviceScope?.launch {
                runPacketLoop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopVpn()
        }
    }

    private suspend fun runPacketLoop() {
        val pfd = vpnInterface ?: return
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(32767)

        try {
            while (serviceScope?.isActive == true && !Thread.currentThread().isInterrupted) {
                val length = inputStream.read(packetBuffer)
                if (length > 0) {
                    processPacket(packetBuffer, length, outputStream)
                }
            }
        } catch (e: IOException) {
            // Stream closed when VPN stopped
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun processPacket(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        val query = DnsPacketParser.parse(packet, length) ?: return
        val startTime = System.currentTimeMillis()

        val (isBlocked, matchedRule) = repository.ruleEngine.isBlocked(query.domain)
        val upstreamServer = repository.selectedDns.value.primaryIp

        if (isBlocked) {
            // Generate Sinkhole (NXDOMAIN / 0.0.0.0) response
            val responsePacket = DnsPacketParser.createBlockedResponsePacket(query)
            try {
                outputStream.write(responsePacket)
                outputStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val latency = System.currentTimeMillis() - startTime
            val log = DnsQueryLog(
                domain = query.domain,
                queryType = query.qTypeName,
                isBlocked = true,
                matchedRule = matchedRule,
                latencyMs = latency,
                upstreamServer = "Local Sinkhole"
            )
            repository.recordQuery(log, length.toLong())
        } else {
            // Forward query to upstream DNS
            val dnsResponsePayload = dnsRelay.forwardQuery(query.rawDnsPayload, upstreamServer)
            val latency = System.currentTimeMillis() - startTime

            if (dnsResponsePayload != null) {
                val responsePacket = DnsPacketParser.createForwardedResponsePacket(query, dnsResponsePayload)
                try {
                    outputStream.write(responsePacket)
                    outputStream.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val log = DnsQueryLog(
                domain = query.domain,
                queryType = query.qTypeName,
                isBlocked = false,
                matchedRule = matchedRule,
                latencyMs = latency,
                upstreamServer = upstreamServer
            )
            repository.recordQuery(log, length.toLong())
        }
    }

    private fun stopVpn() {
        repository.setVpnActive(false)
        serviceScope?.cancel()
        serviceScope = null

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BloklanVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🛡️ Bloklan Aktif")
            .setContentText("Pemblokiran iklan & pelacak sedang berjalan")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(0, "Matikan", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
