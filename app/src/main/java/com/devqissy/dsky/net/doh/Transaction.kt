package com.devqissy.dsky.net.doh

import com.devqissy.dsky.net.dns.DnsPacket
import java.io.Serializable
import java.util.Calendar

/**
 * A representation of a complete DNS transaction, whether it succeeded or failed.
 */
class Transaction(
    @JvmField val queryTime: Long,
    @JvmField val name: String,
    @JvmField val type: Short
) : Serializable {

    @JvmField var responseTime: Long = 0L
    @JvmField var status: Status? = null
    @JvmField var response: ByteArray? = null
    @JvmField var responseCalendar: Calendar? = null
    @JvmField var serverIp: String? = null

    constructor(query: DnsPacket, timestamp: Long) : this(
        queryTime = timestamp,
        name = query.queryName ?: "",
        type = query.queryType
    )

    enum class Status {
        COMPLETE,
        SEND_FAIL,
        HTTP_ERROR,
        BAD_RESPONSE,
        INTERNAL_ERROR,
        CANCELED
    }
}