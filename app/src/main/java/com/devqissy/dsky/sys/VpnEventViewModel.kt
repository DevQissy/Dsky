package com.devqissy.dsky.sys

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.devqissy.dsky.net.doh.Transaction

class VpnEventViewModel : ViewModel() {

    private val _transaction = MutableLiveData<Transaction?>()
    val transaction: LiveData<Transaction?> = _transaction

    private val _dnsStatus = MutableLiveData<Boolean>()
    val dnsStatus: LiveData<Boolean> = _dnsStatus

    fun sendTransaction(transaction: Transaction) {
        _transaction.postValue(transaction)
    }

    fun sendDnsStatus() {
        _dnsStatus.postValue(true)
    }

    fun clearTransaction() {
        _transaction.postValue(null)
    }
}