package com.e.sslapp.customElements

// ---------- Record ----------

data class BluetoothRecordTrigger(
    val start: Long,
    val duration: Long
)

data class BluetoothRecord(
    val data: ArrayList<Short>
)

data class bluetoothRecordAnswer(
    val accepted: Boolean
)

// ---------- Speaker ----------

data class BluetoothSpeakerPosition(
    val x: Float,
    val y: Float
)

data class BluetoothSpeakerSound(
    val start: Long,
    val sound: ArrayList<Short>
)

data class classBluetoothSpeakerTrueStart(
    val start: Long
)