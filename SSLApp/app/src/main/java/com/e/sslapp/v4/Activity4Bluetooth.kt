package com.e.sslapp.v4

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.AudioRecord
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import java.util.*
import kotlin.collections.ArrayList
import java.io.*
import android.util.Log
import com.e.sslapp.v1.Activity1Manual
import com.e.sslapp.v2.Activity2Manual
import com.e.sslapp.v3.Activity3Handler
import com.e.sslapp.R
import kotlinx.android.synthetic.main.activity4_bluetooth.*
import com.beust.klaxon.*
import com.e.sslapp.customElements.BluetoothRecordTrigger
import com.e.sslapp.customElements.bluetoothRecordAnswer



class Activity4Bluetooth : AppCompatActivity() {

    // ------------------------------------------------------------
    //                           Static object
    // ------------------------------------------------------------

    companion object {

        // --------------------
        //      Attributs
        // --------------------

        // var debug: Boolean = false // Use to debug (and for example print in the terminal)

        // --------------------
        //       Methods
        // --------------------

        fun newRecordPath(rootDirectory: File): String {
            var i = 0
            var nRecordPath = rootDirectory.absolutePath + "/recording_$i.pcm"
            while (File(nRecordPath).exists()) {
                i += 1
                nRecordPath = rootDirectory.absolutePath + "/recording_$i.pcm"
            }
            return nRecordPath
        }
    }

    // ------------------------------------------------------------
    //                           Attributs
    // ------------------------------------------------------------

    // ---------- Toolbar ----------
    private var toolbar: Toolbar? = null

    // ---------- Debug options ----------
    private var debug: Boolean = true
    private var saveRecord: Boolean = false
    private var mSaveRecord: Boolean =
        false // Save the state of the save_record switch at the end of the recording

    // ---------- State of the recorder ----------
    private var isRecording: Boolean = false
    private var recordPath: String? = null      // Where data is saved

    // ---------- Variables for AudioRecord ----------
    private val recorderSampleRate: Int = 8000  // For emulator, put 44100 for real phone
    private val recorderChannels = AudioFormat.CHANNEL_IN_MONO
    private val recorderAudioEncoding = AudioFormat.ENCODING_PCM_16BIT
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var bufferElements2Rec: Int =
        1024 // want to play 2048 (2K) since 2 bytes we use only 1024
    private var bytesPerElement: Int = 2 // 2 bytes in 16bit format
    private var bufferSize: Int = AudioRecord.getMinBufferSize(
        recorderSampleRate,
        recorderChannels, recorderAudioEncoding
    )

    // ---------- Handle the recording datas ----------
    private val rootDirectory: File =
        File(Environment.getExternalStorageDirectory().absolutePath + "/SSL")       // Diretory where it is gonna be saved
    private var recordedSound: ArrayList<Short>? = null     // The sound recorder by the phone

    // ---------- Bluetooth ----------
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedBluetoothDevice: BluetoothDevice? = null
    private var uuid: UUID = UUID.fromString("ae465fd9-2d3b-a4c6-4385-ea69b4c1e23c")
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var connected: Boolean = false

    // ------------------------------------------------------------
    //                           Methods
    // ------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {

        // --------------------
        // Called at the creation
        // --------------------

        // -------------------- Set what needs to be --------------------
        getAllIntent()
        changeTheme(debug, onCreate = true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity4_bluetooth)

        // ---------- Handle Toolbar ----------
        toolbar = findViewById(R.id.activity_toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.title = "SSL"
        actionBar?.subtitle = "SecureSoundLocalisation - v4.0"
        actionBar?.elevation = 4.0F

        // ---------- Check the permission ----------
        checkPermission()

        // ----- Create the directory if it doesn't exist -----
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }

        // ---------- Bluetooth ----------
        initBluetooth()

        // -------------------- Call when Start button is pressed --------------------
        button_send_message.setOnClickListener {
            sendMessage()
        }

        button_connect_bluetooth.setOnClickListener{
            changeActivity(Activity4ConnectBluetooth::class.java)
        }

        button_start_connection.setOnClickListener{
            if(connected){
                button_start_connection.text = "Start connection"
                connected = false
                stopConnection()
            } else {
                button_start_connection.text = "Stop connection"
                connected = true
                startConnection()
            }
        }

        // If no bluetooth device connected, ask for connection:
        if(connectedBluetoothDevice == null){
            changeActivity(Activity4ConnectBluetooth::class.java)
        }

        text_paired_device.text = "${connectedBluetoothDevice?.name} - ${connectedBluetoothDevice?.address}"
    }

    private fun getAllIntent() {
        val intent = this.intent
        debug = intent.getBooleanExtra("debug", debug)
        saveRecord = intent.getBooleanExtra("saveRecord", saveRecord)
        connectedBluetoothDevice = intent.getParcelableExtra("connectedBluetoothDevice")
    }

    private fun checkPermission(): Boolean {
        val isNotChecked = (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH
        ) != PackageManager.PERMISSION_GRANTED
                )
        if (isNotChecked) {
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH
            )
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
        return !isNotChecked
    }

    private fun changeTheme(onDebug: Boolean, onCreate: Boolean = false) {
        if (onDebug) {
            debug = true
            setTheme(R.style.DarkTheme)

        } else {
            debug = false
            setTheme(R.style.LightTheme)
        }
        if (!onCreate) {      // To avoid infinite loops
            changeActivity(Activity4Bluetooth::class.java)
        }
    }

    private fun initBluetooth(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    }

    // ------------------------------ Menu ------------------------------

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu4_toolbar, menu)
        initiateMenuItems(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun initiateMenuItems(menu: Menu?) {
        // ----- Activities -----
        val activity = menu?.findItem(R.id.activity_bluetooth)
        activity?.title = "-> Bluetooth <-"
        // ----- Settings -----
        val settingDebug = menu?.findItem(R.id.settings_debug)
        settingDebug?.title = if (debug) "Debug: ON" else "Debug: OFF"
        val settingSaveRecord = menu?.findItem(R.id.settings_save_record)
        settingSaveRecord?.title = if (saveRecord) "Save Record: ON" else "Save Record: OFF"
        // ----- Versions -----
        val version = menu?.findItem(R.id.version_4_0)
        version?.title = "-> v 4.0 <-"
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            // -------------------- Activity menu --------------------
            R.id.activity_manual -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity manual pressed")
                }
                changeActivity(Activity4Manual::class.java)
                return true
            }
            R.id.activity_handler -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity handler pressed")
                }
                changeActivity(Activity4Handler::class.java)
                return true
            }
            R.id.activity_bluetooth -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity bluetooth pressed")
                }
                return true
            }
            R.id.activity_play -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity play pressed")
                }
                changeActivity(Activity4Play::class.java)
                return true
            }
            R.id.activity_speaker -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity speaker pressed")
                }
                changeActivity(Activity4Speaker::class.java)
                return true
            }
            // -------------------- Settings Menu --------------------
            R.id.settings_debug -> {
                Log.d("onOptionsItemSelected", "settings debug pressed")
                if (debug) {
                    debug = false
                    item.title = "Debug: OFF"
                } else {
                    debug = true
                    item.title = "Debug: ON"
                }
                changeTheme(debug)
                return true
            }
            R.id.settings_save_record -> {
                if (saveRecord) {
                    saveRecord = false
                    item.title = "Save Record: OFF"
                } else {
                    saveRecord = true
                    item.title = "Save Record: ON"
                }
                if (debug) {
                    Log.d("onOptionItemSelected", "setting_save_record_pressed")
                }
            }

            // -------------------- Version menu --------------------
            R.id.version_1_0 -> {
                if (debug) {
                    println("v1 pressed")
                }
                changeActivity(Activity1Manual::class.java)
                return true
            }
            R.id.version_2_0 -> {
                if (debug) {
                    println("v2 pressed")
                }
                changeActivity(Activity2Manual::class.java)
                return true
            }
            R.id.version_3_0 -> {
                if (debug) {
                    println("v3 pressed")
                }
                changeActivity(Activity3Handler::class.java)
                return true
            }
            R.id.version_4_0 -> {
                if (debug) {
                    println("v4 pressed")
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun changeActivity(activity: Class<*>) {
        val intent = Intent(this, activity)
        // ----- Put Extra -----
        intent.putExtra("debug", debug)     // Debug value
        intent.putExtra("saveRecord", saveRecord)
        intent.putExtra("connectedBluetoothDevice", connectedBluetoothDevice)
        intent.putExtra("previousActivity", "Bluetooth")
        // ----- Start activity -----
        startActivity(intent)
    }


    // ------------------------------ Helper ------------------------------

    // ---------- Use to change Short to Byte ----------
    infix fun Short.and(that: Int): Int = this.toInt().and(that)

    infix fun Short.shr(that: Int): Int = this.toInt().shr(that)

    private fun short2byte(sData: ShortArray): ByteArray {
        val shortArrsize = sData.size
        val bytes = ByteArray(shortArrsize * 2)
        for (i in 0 until shortArrsize) {
            bytes[i * 2] = (sData[i] and 0x00FF).toByte()
            bytes[i * 2 + 1] = (sData[i] shr 8).toByte()
            sData[i] = 0
        }
        return bytes
    }

    inline fun <reified T> listToArray(list: List<*>): Array<T> {
        // Create a list from an array
        return (list as List<T>).toTypedArray()
    }


    // ------------------------------ Handle button ------------------------------
    private fun startConnection(){
        socket = connectedBluetoothDevice?.createInsecureRfcommSocketToServiceRecord(uuid)
        inputStream = socket?.inputStream
        outputStream = socket?.outputStream
        Toast.makeText(this, "Connection started", Toast.LENGTH_SHORT).show()

        // -----------------------

        try {
            val message = readMessage()
            text_received_message_trigger.text = message
            val messageJSON = Klaxon().parse<BluetoothRecordTrigger>(message)
            text_received_message_trigger_start.text = messageJSON?.start.toString()
            text_received_message_trigger_duration.text = messageJSON?.duration.toString()
        } catch (e: KlaxonException){
            Log.e("sendMessage", "Cannot parse the data", e)
            text_received_message_trigger.text = e.toString()
            text_received_message_trigger_start.text = e.toString()
            text_received_message_trigger_duration.text = e.toString()
        }
    }

    private fun stopConnection(){
        outputStream?.close()
        inputStream?.close()
        socket?.close()
        outputStream = null
        inputStream = null
        socket = null
        Toast.makeText(this, "Connection stopped", Toast.LENGTH_SHORT).show()
    }

    private fun readMessage(): String{
        try{
            val available = inputStream?.available()
            available?.let{
                val bytes = ByteArray(available)
                Log.i("get message", "Reading")
                inputStream?.read(bytes, 0, available)
                val text = String(bytes)
                Log.i("get message", "Message received")
                Log.i("get message", "text: $text")
                return text
            }
        } catch (e: java.lang.Exception){
            Log.e("get message", "Cannot read data", e)
            return e.toString()
        } catch (e: java.lang.NullPointerException){
            Log.e("get message", "Input Stream not available", e)
            return e.toString()
        }
        return ""
    }

    private fun sendMessage() {
        if (debug) {
            Log.d("sendMessage", "Button send massage pressed")
        }
        // Get the device
        /*
        var device: BluetoothDevice? = null
        arrayListPairedBluetoothDevices?.let{
            for(d in it){
                if(d.name == "remi-arch"){
                    device = d
                }
            }
        }
        device?.let {
            BluetoothClient(it).start()
        }
        */

        /*
        connectedBluetoothDevice?.let{
            BluetoothClient(it).start()
        }
        */

        try {
            outputStream?.write(form_message_to_send.text.toString().toByteArray())
            outputStream?.flush()
            Log.i("send message", "Sent")
        } catch (e: Exception) {
            Log.e("send Message", "Cannot send", e)
        }
        Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()

        try{
            val message = readMessage()
            text_received_message_answer.text = message
            val messageJSON = Klaxon().parse<bluetoothRecordAnswer>(message)
            text_received_message_answer_accepted.text = messageJSON?.accepted.toString()
        } catch (e: KlaxonException){
            Log.e("sendMessage", "Cannot parse the data", e)
            text_received_message_answer.text = e.toString()
            text_received_message_answer_accepted.text = e.toString()
        }
    }
}





