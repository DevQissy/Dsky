package com.devqissy.dsky.sys

/**
 * Interface for classes that can receive information about when recent events occurred.
 * Allows HistoryGraph to scan QueryTracker's activity data while QueryTracker holds a lock,
 * without making an extra copy.
 */
fun interface ActivityReceiver {
    fun receive(activity: Collection<Long>)
}