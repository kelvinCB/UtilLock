package app.utillock.android.filter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import app.utillock.android.MainActivity
import app.utillock.android.R
import app.utillock.android.UtilLockApplication
import app.utillock.android.model.DomainMatcher
import app.utillock.android.model.ScheduleEvaluator
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AdultFilterVpnService : VpnService() {
    private val running = AtomicBoolean(false)
    private var tunnel: ParcelFileDescriptor? = null
    private val repository by lazy { (application as UtilLockApplication).container.protectionRepository }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        if (running.compareAndSet(false, true)) startTunnel()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun startTunnel() {
        val builder = Builder()
            .setSession("UtilLock DNS")
            .setMtu(1500)
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_DNS, 32)
            .addDnsServer(VPN_DNS)
        runCatching { builder.addDisallowedApplication(packageName) }
        tunnel = builder.establish()
        val descriptor = tunnel ?: run {
            running.set(false)
            return
        }
        thread(name = "utillock-dns", isDaemon = true) { packetLoop(descriptor) }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val packet = ByteArray(32767)
        while (running.get()) {
            val size = runCatching { input.read(packet) }.getOrElse { break }
            if (size <= 0) continue
            val request = DnsPacket.parse(packet, size) ?: continue
            val active = ScheduleEvaluator.activeProtection(repository.snapshot())
            val blocked = active.active && DomainMatcher.matches(request.host, active.domains, emptySet())
            val dnsResponse = when {
                blocked -> DnsPacket.nxDomain(request.payload)
                else -> queryDoh(
                    request.payload,
                    familyFilter = active.active && active.adultFilter,
                )
            } ?: DnsPacket.serverFailure(request.payload)
            val responsePacket = DnsPacket.wrapResponse(request, dnsResponse)
            runCatching { output.write(responsePacket) }
        }
    }

    private fun queryDoh(query: ByteArray, familyFilter: Boolean): ByteArray? = runCatching {
        val endpoint = if (familyFilter) FAMILY_DOH else STANDARD_DOH
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Accept", "application/dns-message")
            setRequestProperty("Content-Type", "application/dns-message")
        }
        connection.outputStream.use { it.write(query) }
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.use { it.readBytes() }
    }.getOrNull()

    private fun stopTunnel() {
        running.set(false)
        runCatching { tunnel?.close() }
        tunnel = null
        repository.setDnsVpn(false)
    }

    private fun notification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.web_protection_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AdultFilterVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "app.utillock.android.START_DNS_VPN"
        const val ACTION_STOP = "app.utillock.android.STOP_DNS_VPN"
        private const val CHANNEL_ID = "web-protection"
        private const val NOTIFICATION_ID = 2101
        private const val VPN_ADDRESS = "10.23.0.1"
        private const val VPN_DNS = "10.23.0.2"
        private const val FAMILY_DOH = "https://family.cloudflare-dns.com/dns-query"
        private const val STANDARD_DOH = "https://cloudflare-dns.com/dns-query"
    }
}

object VpnController {
    fun permissionIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        context.startForegroundService(
            Intent(context, AdultFilterVpnService::class.java).setAction(AdultFilterVpnService.ACTION_START),
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, AdultFilterVpnService::class.java).setAction(AdultFilterVpnService.ACTION_STOP),
        )
    }
}

private data class DnsPacket(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val identification: Int,
    val host: String,
    val payload: ByteArray,
) {
    companion object {
        fun parse(packet: ByteArray, size: Int): DnsPacket? {
            if (size < 40 || (packet[0].toInt() ushr 4) != 4) return null
            val headerLength = (packet[0].toInt() and 0x0F) * 4
            if (headerLength < 20 || size < headerLength + 8 || packet[9].toInt() != 17) return null
            val sourcePort = ushort(packet, headerLength)
            val destinationPort = ushort(packet, headerLength + 2)
            if (destinationPort != 53) return null
            val dnsOffset = headerLength + 8
            val dnsLength = minOf(ushort(packet, headerLength + 4) - 8, size - dnsOffset)
            if (dnsLength < 17) return null
            val payload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
            val host = readQuestionName(payload) ?: return null
            return DnsPacket(
                sourceAddress = packet.copyOfRange(12, 16),
                destinationAddress = packet.copyOfRange(16, 20),
                sourcePort = sourcePort,
                destinationPort = destinationPort,
                identification = ushort(packet, 4),
                host = host,
                payload = payload,
            )
        }

        fun wrapResponse(request: DnsPacket, dns: ByteArray): ByteArray {
            val total = 20 + 8 + dns.size
            val buffer = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
            buffer.put(0x45.toByte())
            buffer.put(0)
            buffer.putShort(total.toShort())
            buffer.putShort(request.identification.toShort())
            buffer.putShort(0x4000.toShort())
            buffer.put(64)
            buffer.put(17)
            buffer.putShort(0)
            buffer.put(request.destinationAddress)
            buffer.put(request.sourceAddress)
            buffer.putShort(request.destinationPort.toShort())
            buffer.putShort(request.sourcePort.toShort())
            buffer.putShort((8 + dns.size).toShort())
            buffer.putShort(0) // UDP checksum may be zero for IPv4.
            buffer.put(dns)
            val result = buffer.array()
            val checksum = internetChecksum(result, 0, 20)
            result[10] = (checksum ushr 8).toByte()
            result[11] = checksum.toByte()
            return result
        }

        fun nxDomain(query: ByteArray): ByteArray = errorResponse(query, 3)
        fun serverFailure(query: ByteArray): ByteArray = errorResponse(query, 2)

        private fun errorResponse(query: ByteArray, code: Int): ByteArray {
            val questionEnd = questionEnd(query).coerceAtMost(query.size)
            val response = query.copyOf(questionEnd)
            val originalFlags = ushort(query, 2)
            val flags = originalFlags or 0x8000 or 0x0080 or code
            response[2] = (flags ushr 8).toByte()
            response[3] = flags.toByte()
            for (index in 6..11) response[index] = 0
            return response
        }

        private fun questionEnd(payload: ByteArray): Int {
            var offset = 12
            while (offset < payload.size && payload[offset].toInt() != 0) {
                offset += 1 + (payload[offset].toInt() and 0xFF)
            }
            return (offset + 5).coerceAtMost(payload.size)
        }

        private fun readQuestionName(payload: ByteArray): String? {
            var offset = 12
            val labels = mutableListOf<String>()
            while (offset < payload.size) {
                val length = payload[offset].toInt() and 0xFF
                if (length == 0) break
                if (length > 63 || offset + 1 + length > payload.size) return null
                labels += payload.copyOfRange(offset + 1, offset + 1 + length).toString(Charsets.UTF_8)
                offset += length + 1
            }
            return labels.joinToString(".")
        }

        private fun ushort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

        private fun internetChecksum(bytes: ByteArray, offset: Int, length: Int): Int {
            var sum = 0L
            var index = offset
            while (index < offset + length) {
                sum += (((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)).toLong()
                index += 2
            }
            while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
            return sum.inv().toInt() and 0xFFFF
        }
    }
}
