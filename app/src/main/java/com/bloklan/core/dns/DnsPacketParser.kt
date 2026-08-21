package com.bloklan.core.dns

import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ParsedDnsQuery(
    val id: Int,
    val domain: String,
    val qType: Int,
    val qClass: Int,
    val srcIp: ByteArray,
    val dstIp: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val rawDnsPayload: ByteArray,
    val ipHeaderLength: Int,
    val isIpv6: Boolean = false
) {
    val qTypeName: String
        get() = when (qType) {
            1 -> "A"
            28 -> "AAAA"
            5 -> "CNAME"
            15 -> "MX"
            16 -> "TXT"
            65 -> "HTTPS"
            255 -> "ANY"
            else -> "TYPE_$qType"
        }
}

object DnsPacketParser {

    fun parse(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < 28) return null // Min IPv4 (20) + UDP (8)

        val buffer = ByteBuffer.wrap(packet, 0, length)
        val versionAndIhl = buffer.get(0).toInt() and 0xFF
        val version = versionAndIhl ushr 4

        if (version == 4) {
            val ihl = (versionAndIhl and 0x0F) * 4
            if (length < ihl + 8) return null

            val protocol = buffer.get(9).toInt() and 0xFF
            if (protocol != 17) return null // Only UDP

            val srcIp = ByteArray(4)
            val dstIp = ByteArray(4)
            System.arraycopy(packet, 12, srcIp, 0, 4)
            System.arraycopy(packet, 16, dstIp, 0, 4)

            val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
            val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
            val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)

            if (dstPort != 53 && srcPort != 53) return null
            if (length < ihl + udpLength || udpLength < 8) return null

            val dnsPayloadOffset = ihl + 8
            val dnsPayloadLength = udpLength - 8
            if (dnsPayloadLength < 12) return null // Min DNS Header

            val dnsBuffer = ByteBuffer.wrap(packet, dnsPayloadOffset, dnsPayloadLength).order(ByteOrder.BIG_ENDIAN)
            val dnsId = dnsBuffer.short.toInt() and 0xFFFF
            val flags = dnsBuffer.short.toInt() and 0xFFFF
            val isResponse = (flags and 0x8000) != 0
            if (isResponse) return null // We only parse queries

            val qdCount = dnsBuffer.short.toInt() and 0xFFFF
            if (qdCount < 1) return null

            // Skip ancount, nscount, arcount
            dnsBuffer.short
            dnsBuffer.short
            dnsBuffer.short

            val domain = parseDomainName(dnsBuffer) ?: return null
            val qType = dnsBuffer.short.toInt() and 0xFFFF
            val qClass = dnsBuffer.short.toInt() and 0xFFFF

            val rawDns = ByteArray(dnsPayloadLength)
            System.arraycopy(packet, dnsPayloadOffset, rawDns, 0, dnsPayloadLength)

            return ParsedDnsQuery(
                id = dnsId,
                domain = domain,
                qType = qType,
                qClass = qClass,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = srcPort,
                dstPort = dstPort,
                rawDnsPayload = rawDns,
                ipHeaderLength = ihl,
                isIpv6 = false
            )
        }

        return null
    }

    private fun parseDomainName(buffer: ByteBuffer): String? {
        val sb = StringBuilder()
        while (buffer.hasRemaining()) {
            val len = buffer.get().toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0) {
                // Compressed pointer, skip 1 byte
                if (buffer.hasRemaining()) buffer.get()
                break
            }
            if (buffer.remaining() < len) return null
            val labelBytes = ByteArray(len)
            buffer.get(labelBytes)
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(labelBytes, Charsets.US_ASCII))
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /**
     * Builds a blocked response packet (IPv4 + UDP + DNS Sinkhole Answer).
     * For A queries: Returns A record pointing to 0.0.0.0
     * For other queries: Returns NXDOMAIN (RCODE = 3)
     */
    fun createBlockedResponsePacket(query: ParsedDnsQuery): ByteArray {
        val dnsResponsePayload = createBlockedDnsPayload(query)

        val totalIpLength = 20 + 8 + dnsResponsePayload.size
        val packet = ByteArray(totalIpLength)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // 1. IP Header (20 bytes)
        buf.put(0x45.toByte()) // Version 4, IHL 5
        buf.put(0x00.toByte()) // DSCP / ECN
        buf.putShort(totalIpLength.toShort()) // Total Length
        buf.putShort(0x0000.toShort()) // Identification
        buf.putShort(0x4000.toShort()) // Flags (Don't Fragment) + Fragment Offset
        buf.put(64.toByte()) // TTL
        buf.put(17.toByte()) // Protocol (UDP)
        buf.putShort(0.toShort()) // Checksum placeholder
        buf.put(query.dstIp) // New Src IP = original Dst IP
        buf.put(query.srcIp) // New Dst IP = original Src IP

        // Compute IP Checksum
        val ipChecksum = computeIpChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. UDP Header (8 bytes)
        buf.position(20)
        buf.putShort(query.dstPort.toShort()) // New Src Port = original Dst Port
        buf.putShort(query.srcPort.toShort()) // New Dst Port = original Src Port
        val udpLength = 8 + dnsResponsePayload.size
        buf.putShort(udpLength.toShort())
        buf.putShort(0.toShort()) // UDP Checksum (0 = disabled / optional in IPv4)

        // 3. DNS Payload
        buf.put(dnsResponsePayload)

        return packet
    }

    /**
     * Constructs a valid DNS packet bytes answering with 0.0.0.0 or NXDOMAIN
     */
    fun createBlockedDnsPayload(query: ParsedDnsQuery): ByteArray {
        val out = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)

        // Transaction ID
        out.putShort(query.id.toShort())

        val isTypeA = query.qType == 1
        if (isTypeA) {
            // Flags: Standard query response, No error, Authoritative, Recursion Desired + Available
            // 0x8180 -> QR=1, Opcode=0, AA=1, TC=0, RD=1, RA=1, Z=0, RCODE=0
            out.putShort(0x8580.toShort())
            out.putShort(1.toShort()) // QDCOUNT = 1
            out.putShort(1.toShort()) // ANCOUNT = 1
            out.putShort(0.toShort()) // NSCOUNT = 0
            out.putShort(0.toShort()) // ARCOUNT = 0
        } else {
            // NXDOMAIN Flags: 0x8183 -> QR=1, RD=1, RA=1, RCODE=3 (Name Error)
            out.putShort(0x8183.toShort())
            out.putShort(1.toShort()) // QDCOUNT = 1
            out.putShort(0.toShort()) // ANCOUNT = 0
            out.putShort(0.toShort()) // NSCOUNT = 0
            out.putShort(0.toShort()) // ARCOUNT = 0
        }

        // Write Question Section
        val qnameOffset = out.position()
        writeDomainName(out, query.domain)
        out.putShort(query.qType.toShort())
        out.putShort(query.qClass.toShort())

        // Write Answer Section (if Type A)
        if (isTypeA) {
            // Pointer to QNAME (compressed: 0xC000 | qnameOffset)
            out.putShort((0xC000 or qnameOffset).toShort())
            out.putShort(1.toShort()) // TYPE = A
            out.putShort(1.toShort()) // CLASS = IN
            out.putInt(300) // TTL = 300 seconds
            out.putShort(4.toShort()) // RDLENGTH = 4 bytes
            out.put(byteArrayOf(0, 0, 0, 0)) // RDATA = 0.0.0.0
        }

        val result = ByteArray(out.position())
        System.arraycopy(out.array(), 0, result, 0, out.position())
        return result
    }

    private fun writeDomainName(buffer: ByteBuffer, domain: String) {
        val labels = domain.split('.')
        for (label in labels) {
            if (label.isNotEmpty()) {
                val bytes = label.toByteArray(Charsets.US_ASCII)
                buffer.put(bytes.size.toByte())
                buffer.put(bytes)
            }
        }
        buffer.put(0.toByte())
    }

    /**
     * Constructs forwarded IP/UDP packet wrapping the upstream DNS response
     */
    fun createForwardedResponsePacket(query: ParsedDnsQuery, upstreamDnsPayload: ByteArray): ByteArray {
        val totalIpLength = 20 + 8 + upstreamDnsPayload.size
        val packet = ByteArray(totalIpLength)
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // 1. IP Header
        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalIpLength.toShort())
        buf.putShort(0x0000.toShort())
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte())
        buf.putShort(0.toShort())
        buf.put(query.dstIp) // Src = original Dst
        buf.put(query.srcIp) // Dst = original Src

        val ipChecksum = computeIpChecksum(packet, 0, 20)
        packet[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // 2. UDP Header
        buf.position(20)
        buf.putShort(query.dstPort.toShort())
        buf.putShort(query.srcPort.toShort())
        val udpLength = 8 + upstreamDnsPayload.size
        buf.putShort(udpLength.toShort())
        buf.putShort(0.toShort())

        // 3. Upstream DNS payload
        buf.put(upstreamDnsPayload)

        return packet
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            val word = (high shl 8) or low
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv()) and 0xFFFF
    }
}
