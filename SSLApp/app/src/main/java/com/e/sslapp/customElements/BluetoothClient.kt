package com.e.sslapp.customElements

import android.bluetooth.BluetoothDevice
import android.util.Log
import java.util.*

// TODO: Get the UUID automatically
val uuid_: UUID = UUID.fromString("8989063a-c9af-463a-b3f1-f21d9b2b827b")
val message_: String = "My Message default"

class BluetoothClient(device: BluetoothDevice, uuid: UUID = uuid_, message: String = message_) :
    Thread() {
    private val socket = device.createRfcommSocketToServiceRecord(uuid)
    private val mMessage = message

    override fun run() {
        Log.d("Bluetooth Client", "In run function")
        /*
        Log.i("client", "Connecting")
        this.socket.connect()

        Log.i("client", "Sending")
        val outputStream = this.socket.outputStream
        val inputStream = this.socket.inputStream
        try {
            outputStream.write(mMessage.toByteArray())
            outputStream.flush()
            Log.i("client", "Sent")
        } catch (e: Exception) {
            Log.e("client", "Cannot send", e)
        } finally {
            outputStream.close()
            inputStream.close()
            this.socket.close()
        }

         */
    }
}