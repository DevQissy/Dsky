package com.devqissy.dsky.net.doh

/**
 * A prober can perform asynchronous checks to determine whether a DOH server is working.
 */
abstract class Prober {
    fun interface Callback {
        fun onCompleted(succeeded: Boolean)
    }

    abstract fun probe(url: String, callback: Callback)
}