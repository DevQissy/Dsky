package com.devqissy.dsky.net.dns

import java.net.InetAddress
import java.net.ProtocolException
import java.net.UnknownHostException
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/**
 * A representation of a DNS query or response packet. Provides read-only access to
 * the relevant contents of a DNS query packet.
 */
class DnsPacket @Throws(ProtocolException::class) constructor(private val data: ByteArray) {

    companion object {
        private const val TYPE_A: Short = 1
        private const val TYPE_AAAA: Short = 28

        private fun getBit(src: Byte, index: Int): Boolean =
            (src.toInt() and (1 shl index)) != 0

        private fun getBits(src: Byte, start: Int, size: Int): Byte {
            val mask = (1 shl size) - 1
            return ((src.toInt() ushr start) and mask).toByte()
        }

        @Throws(BufferUnderflowException::class, ProtocolException::class)
        private fun readName(buffer: ByteBuffer): String {
            val nameBuffer = StringBuilder()
            var labelLength = buffer.get()
            while (labelLength > 0) {
                val labelBytes = ByteArray(labelLength.toInt())
                buffer.get(labelBytes)
                nameBuffer.append(String(labelBytes))
                nameBuffer.append(".")
                labelLength = buffer.get()
            }
            if (labelLength < 0) {
                val barrier = buffer.position() - 1
                val offsetHighBits = getBits(labelLength, 0, 6)
                val offsetLowBits = buffer.get()
                val offset = (offsetHighBits.toInt() shl 8) or (offsetLowBits.toInt() and 0xFF)
                val arr = buffer.array()
                val delta = barrier - offset
                if (offset < 0 || delta < 0) throw ProtocolException("Bad compressed name")
                val referenceBuffer = ByteBuffer.wrap(arr, offset, delta)
                nameBuffer.append(readName(referenceBuffer))
            }
            return nameBuffer.toString()
        }

        @Throws(BufferUnderflowException::class, ProtocolException::class)
        private fun readRecords(src: ByteBuffer, numRecords: Short): Array<DnsRecord> {
            return Array(numRecords.toInt()) {
                val name = readName(src)
                val rtype = src.short
                val rclass = src.short
                val ttl = src.int
                val dataLen = src.short.toInt()
                val recordData = ByteArray(dataLen)
                src.get(recordData)
                DnsRecord(name, rtype, rclass, ttl, recordData)
            }
        }
    }

    private class DnsQuestion(val name: String, val qtype: Short, val qclass: Short)
    private class DnsRecord(val name: String, val rtype: Short, val rclass: Short, val ttl: Int, val data: ByteArray)

    private val id: Short
    private val qr: Boolean
    private val opcode: Byte
    private val aa: Boolean
    private val tc: Boolean
    private val rd: Boolean
    private val ra: Boolean
    private val z: Byte
    private val rcode: Byte
    private val question: Array<DnsQuestion>
    private val answer: Array<DnsRecord>
    private val authority: Array<DnsRecord>
    private val additional: Array<DnsRecord>

    val queryName: String? get() = question.firstOrNull()?.name
    val queryType: Short get() = question.firstOrNull()?.qtype ?: 0

    init {
        val buffer = ByteBuffer.wrap(data)
        try {
            id = buffer.short

            val flags1 = buffer.get()
            qr = getBit(flags1, 7)
            opcode = getBits(flags1, 3, 4)
            aa = getBit(flags1, 2)
            tc = getBit(flags1, 1)
            rd = getBit(flags1, 0)

            val flags2 = buffer.get()
            ra = getBit(flags2, 7)
            z = getBits(flags2, 4, 3)
            rcode = getBits(flags2, 0, 4)

            val numQuestions = buffer.short
            val numAnswers = buffer.short
            val numAuthorities = buffer.short
            val numAdditional = buffer.short

            question = Array(numQuestions.toInt()) {
                val name = readName(buffer)
                val qtype = buffer.short
                val qclass = buffer.short
                DnsQuestion(name, qtype, qclass)
            }
            answer = readRecords(buffer, numAnswers)
            authority = readRecords(buffer, numAuthorities)
            additional = readRecords(buffer, numAdditional)
        } catch (e: BufferUnderflowException) {
            throw ProtocolException("Packet too short").also { it.initCause(e) }
        }
    }

    fun getId(): Short = id

    fun isNormalQuery(): Boolean =
        !qr && question.isNotEmpty() && z == 0.toByte() && authority.isEmpty() && answer.isEmpty()

    fun isResponse(): Boolean = qr

    fun getResponseAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        for (src in arrayOf(answer, authority)) {
            for (r in src) {
                if (r.rtype == TYPE_A || r.rtype == TYPE_AAAA) {
                    try {
                        addresses.add(InetAddress.getByAddress(r.data))
                    } catch (e: UnknownHostException) {
                        // ignore
                    }
                }
            }
        }
        return addresses
    }
}