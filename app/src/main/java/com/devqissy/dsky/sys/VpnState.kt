package com.devqissy.dsky.sys

class VpnState(
    // Whether the user has requested that the VPN be active. This is persistent state, sync'd to disk.
    @JvmField val activationRequested: Boolean,
    // Whether the VPN is running. When this is true a key icon is showing in the status bar.
    @JvmField val on: Boolean,
    // Whether we have a connection to a DOH server, and if so, whether the connection is ready or
    // has recently been failing.
    @JvmField val connectionState: IntraVpnService.State?
)