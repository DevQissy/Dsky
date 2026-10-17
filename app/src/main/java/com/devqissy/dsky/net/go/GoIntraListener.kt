package com.devqissy.dsky.net.go

import android.util.Log
import com.devqissy.dsky.net.dns.DnsPacket
import com.devqissy.dsky.net.doh.Transaction
import com.devqissy.dsky.sys.IntraVpnService
import backend.DoHListener
import backend.DoHQuerySumary
import backend.DoHQueryToken
import dsky.Listener
import dsky.TCPSocketSummary
import dsky.UDPSocketSummary
import java.net.ProtocolException
import java.util.Calendar

/**
 * Callback class passed to go-tun2socks.
 * Simple version without Firebase dependencies.
 */
class GoIntraListener(
    private val vpnService: IntraVpnService
) : Listener, DoHListener {

    companion object {
        private const val TAG = "GoIntraListener"
        private const val UDP_THRESHOLD_BYTES = 10000L
    }

    override fun onTCPSocketClosed(summary: TCPSocketSummary) {
        Log.d(TAG, "TCP Closed: upload=${summary.getUploadBytes()}, " +
                "download=${summary.getDownloadBytes()}, " +
                "port=${summary.getServerPort()}, " +
                "synack=${summary.getSynack()}, " +
                "duration=${summary.getDuration()}")

        val retry = summary.getRetry()
        if (retry != null && retry.getSplit() != 0.toShort()) {
            Log.d(TAG, "Early Reset: bytes=${retry.getBytes()}, " +
                    "chunks=${retry.getChunks()}, " +
                    "timeout=${retry.getTimeout()}, " +
                    "split=${retry.getSplit()}, " +
                    "hasDownload=${summary.getDownloadBytes() > 0}")
        }
    }

    override fun onUDPSocketClosed(summary: UDPSocketSummary) {
        val totalBytes = summary.getUploadBytes() + summary.getDownloadBytes()
        if (totalBytes < UDP_THRESHOLD_BYTES) return
        Log.d(TAG, "UDP Closed: upload=${summary.getUploadBytes()}, " +
                "download=${summary.getDownloadBytes()}, " +
                "duration=${summary.getDuration()}")
    }

    override fun onQuery(url: String): DoHQueryToken {
        Log.d(TAG, "DoH Query started: $url")
        return object : DoHQueryToken {}
    }

    override fun onResponse(token: DoHQueryToken?, summary: DoHQuerySumary) {
        val query = try {
            DnsPacket(summary.getQuery())
        } catch (e: ProtocolException) {
            Log.e(TAG, "Invalid DNS packet", e)
            return
        }

        val latencyMs = (1000 * summary.getLatency()).toLong()
        val nowMs = System.currentTimeMillis()

        Log.d(TAG, "DoH Response: query=${query}, " +
                "status=${summary.getStatus()}, " +
                "latency=${latencyMs}ms, " +
                "server=${summary.getServer()}")

        val transaction = Transaction(query, nowMs - latencyMs).apply {
            response = summary.getResponse()
            responseTime = latencyMs
            serverIp = summary.getServer()
            status = getStatus(summary.getStatus())
            responseCalendar = Calendar.getInstance()
        }
        vpnService.recordTransaction(transaction)
    }

    private fun getStatus(status: Long): Transaction.Status {
        return when (status.toInt()) {
            0 -> Transaction.Status.COMPLETE
            1 -> Transaction.Status.SEND_FAIL
            2 -> Transaction.Status.HTTP_ERROR
            3, 5 -> Transaction.Status.INTERNAL_ERROR
            4 -> Transaction.Status.BAD_RESPONSE
            else -> Transaction.Status.INTERNAL_ERROR
        }
    }
}