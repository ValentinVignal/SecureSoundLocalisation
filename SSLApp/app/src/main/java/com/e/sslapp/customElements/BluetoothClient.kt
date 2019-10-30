package com.e.sslapp.customElements

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.collections.ArrayList

// TODO: Get the UUID automatically
val uuid_: UUID = UUID.fromString("ae465fd9-2d3b-a4c6-4385-ea69b4c1e23c")
const val message_: String = "My Message default"

class BluetoothClient(device: BluetoothDevice, uuid: UUID = uuid_, message: String = message_) :
    Thread() {
    private val socket = device.createRfcommSocketToServiceRecord(uuid)
    private val mMessage = message
    override fun run() {
        Log.i("client", "Connecting")
        this.socket.connect()

        Log.i("client", "Sending")
        val outputStream = this.socket.outputStream
        val inputStream = this.socket.inputStream

        var text1 = ""
        var text2 = ""
        var text3 = ""
        // ---------- Read Data 1 ----------
        try{
            val available = inputStream.available()
            val bytes = ByteArray(available)
            Log.i("get message 1", "Reading")
            inputStream.read(bytes, 0, available)
            val text1 = String(bytes)
            Log.i("get message 1", "Message received")
            Log.i("get message 1", text1)
            Log.i("get message 1", "text: $text1")
        } catch (e: java.lang.Exception){
            Log.e("get message 1", "Cannot read data", e)
        }
        // ---------- Read Data 2 ----------
        try{
            val available = inputStream.available()
            val bytes = ByteArray(available)
            Log.i("get message 2", "Reading")
            inputStream.read(bytes, 0, available)
            val text2 = String(bytes)
            Log.i("get message 2", "Message received")
            Log.i("get message 2", text2)
            Log.i("get message 2", "text: $text2")
        } catch (e: java.lang.Exception){
            Log.e("get message 2", "Cannot read data", e)
        }
        // ---------- Send Data ----------
        try {
            outputStream.write(mMessage.toByteArray())
            outputStream.flush()
            Log.i("send message", "Sent")
        } catch (e: Exception) {
            Log.e("send Message", "Cannot send", e)
        }
        // ---------- Read Data 3 ----------
        try{
            val available = inputStream.available()
            val bytes = ByteArray(available)
            Log.i("get message 3", "Reading")
            inputStream.read(bytes, 0, available)
            val text3 = String(bytes)
            Log.i("get message 3", "Message received")
            Log.i("get message 3", text3)
            Log.i("get message 3", "text: $text3")
        } catch (e: java.lang.Exception){
            Log.e("get message 3", "Cannot read data", e)
        }
        // ---------- End Communication ----------
        finally {
            outputStream.close()
            inputStream.close()
            this.socket.close()
        }
    }
}