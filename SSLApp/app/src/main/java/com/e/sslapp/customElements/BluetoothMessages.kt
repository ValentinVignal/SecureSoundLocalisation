package com.e.sslapp.customElements

data class BluetoothTrigger(
    val start: Long,
    val duration: Long
)

data class BluetoothRecord(
    val data: ArrayList<Short>
)

data class BluetoothAnswer(
    val accepted: Boolean
)