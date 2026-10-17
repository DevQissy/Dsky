package com.devqissy.dsky.net.doh

import android.content.Context
import android.util.Log
import com.devqissy.dsky.net.go.GoProber

/**
 * Performs parallel probes to all specified servers and calls the listener when
 * the fastest probe succeeds or all probes have failed. Each instance can only be used once.
 */
object Race {
    private const val TAG = "DoHProbe"

    fun interface Listener {
        fun onResult(index: Int)
    }

    @JvmStatic
    fun start(context: Context, urls: Array<String>, listener: Listener) {
        start(GoProber(context), urls, listener)
    }

    // Exposed for unit testing only.
    internal fun start(prober: Prober, urls: Array<String>, listener: Listener) {
        val collector = Collector(urls.size, listener)
        urls.forEachIndexed { i, url ->
            prober.probe(url) { succeeded -> collector.onCompleted(i, succeeded) }
        }
    }

    private class Collector(
        private val numCallbacks: Int,
        private val listener: Listener
    ) {
        private var numFailed = 0
        private var reportedSuccess = false

        @Synchronized
        fun onCompleted(index: Int, succeeded: Boolean) {
            if (succeeded) {
                Log.i(TAG, "DoH Server No. $index: succeeded")
                if (!reportedSuccess) {
                    listener.onResult(index)
                    reportedSuccess = true
                }
            } else {
                Log.w(TAG, "DoH Server No. $index: failed")
                if (++numFailed == numCallbacks) {
                    listener.onResult(-1)
                }
            }
        }
    }
}
