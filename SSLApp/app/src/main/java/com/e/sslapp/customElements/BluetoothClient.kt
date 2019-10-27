package com.e.sslapp.customElements

import android.bluetooth.BluetoothDevice
import android.util.Log
import java.util.*

// TODO: Get the UUID automatically
val uuid_: UUID = UUID.fromString("ae465fd9-2d3b-a4c6-4385-ea69b4c1e23c")
val message_: String = "My Message default"

class BluetoothClient(device: BluetoothDevice, uuid: UUID = uuid_, message: String = message_) :
    Thread() {
    private val socket = device.createRfcommSocketToServiceRecord(uuid)
    private val number = 123456789
    private val mMessage = message_


    //private val mM: Array<Byte> = BitConverter

    override fun run() {
        Log.d("Bluetooth Client", "In run function")
        Log.i("client", "Connecting")
        this.socket.connect()

        Log.i("client", "Sending")
        val outputStream = this.socket.outputStream
        val inputStream = this.socket.inputStream
        // ---------- Read Data ----------
        try{
            val available = inputStream.available()
            val bytes = ByteArray(available)
            Log.i("server", "Reading")
            inputStream.read(bytes, 0, available)
            val text = String(bytes)
            Log.i("server", "Message received")
            Log.i("server", text)
            Log.i("server", "text: $text")
        } catch (e: java.lang.Exception){
            Log.e("server", "Cannot read data", e)

        }
        // ---------- Send Data ----------
        try {
            outputStream.write(number.toString().toByteArray())
            outputStream.flush()
            Log.i("client", "Sent")
        } catch (e: Exception) {
            Log.e("client", "Cannot send", e)
        } finally {
            outputStream.close()
            inputStream.close()
            this.socket.close()
        }

    }
}