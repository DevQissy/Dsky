package com.devqissy.dsky.sys

import android.content.Context
import android.content.Context.MODE_PRIVATE
import com.devqissy.dsky.net.doh.Transaction
import java.util.Collections
import java.util.LinkedList

/**
 * A class for tracking DNS transactions. Counts successful transactions,
 * records the last minute of query timestamps, and optionally maintains a history.
 * Thread-safe.
 */
class QueryTracker(context: Context) {

    private companion object {
        const val NUM_REQUESTS = "numRequests"
        const val HISTORY_SIZE = 100
        const val ACTIVITY_MEMORY_MS = 60 * 1000L
    }

    private var numRequests: Long = 0
    private val recentTransactions: LinkedList<Transaction> = LinkedList()
    private val recentActivity: LinkedList<Long> = LinkedList()
    private var historyEnabled = false

    init {
        sync(context)
    }

    @Synchronized
    fun getNumRequests(): Long = numRequests

    @Synchronized
    fun getRecentTransactions(): LinkedList<Transaction> = LinkedList(recentTransactions)

    @Synchronized
    fun showActivity(receiver: ActivityReceiver) {
        receiver.receive(Collections.unmodifiableCollection(recentActivity))
    }

    @Synchronized
    fun countQueriesSince(startTime: Long): Int {
        var queries = recentActivity.size
        for (time in recentActivity) {
            if (time < startTime) --queries else break
        }
        return queries
    }

    @Synchronized
    fun setHistoryEnabled(enabled: Boolean) {
        historyEnabled = enabled
        if (!enabled) recentTransactions.clear()
    }

    fun isHistoryEnabled(): Boolean = historyEnabled

    @Synchronized
    fun recordTransaction(context: Context, transaction: Transaction) {
        if (transaction.status == Transaction.Status.COMPLETE) {
            ++numRequests
            if (numRequests % HISTORY_SIZE == 0L) sync(context)
        }

        recentActivity.add(transaction.queryTime)
        while ((recentActivity.peek() ?: 0) + ACTIVITY_MEMORY_MS < transaction.queryTime) {
            recentActivity.remove()
        }

        if (historyEnabled) {
            recentTransactions.add(transaction)
            if (recentTransactions.size > HISTORY_SIZE) recentTransactions.remove()
        }
    }

    @Synchronized
    fun sync(context: Context) {
        val settings = context.getSharedPreferences(
            QueryTracker::class.java.simpleName, MODE_PRIVATE
        )
        val storedNumRequests = settings.getLong(NUM_REQUESTS, 0)
        if (storedNumRequests >= numRequests) {
            numRequests = storedNumRequests
        } else {
            settings.edit().putLong(NUM_REQUESTS, numRequests).apply()
        }
    }
}