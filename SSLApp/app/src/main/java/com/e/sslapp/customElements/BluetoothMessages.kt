package com.e.sslapp.customElements

import android.bluetooth.BluetoothSocket
import android.util.Log

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

data class BluetoothSpeakerTrueStart(
    val start: Long
)



fun readMessage(socket: BluetoothSocket): String{
    try{
        var available = socket.inputStream?.available()
        while(available == 0){
            Thread.sleep(200)
            available = socket.inputStream?.available()
        }
        var text = ""
        while (available != 0){
            available?.let{ itAvailable ->
                val bytes = ByteArray(itAvailable)
                Log.i("readMessage", "Reading")
                socket.inputStream?.read(bytes, 0, itAvailable)
                Log.i("readMessage", "InputStream ${socket.inputStream}")
                Log.i("readMessage", "available $itAvailable")
                text += String(bytes)
                Log.i("readMessage", "Message received: text $text")
                Thread.sleep(100)
                available = socket.inputStream?.available()
            }
        }
        return text
    } catch (e: java.lang.Exception){
        Log.e("readMessage", "Cannot read data", e)
        return e.toString()
    } catch (e: java.lang.NullPointerException){
        Log.e("readMessage", "Input Stream not available", e)
        return e.toString()
    }
}

fun sendMessage(socket: BluetoothSocket, message:String){
    try {
        socket.outputStream?.write(message.toByteArray())
        socket.outputStream?.flush()
        Log.i("sendStartPlayTruth", "Sent")
    } catch (e: Exception) {
        Log.e("sendStartPlayTruth", "Cannot send", e)
    }

}



