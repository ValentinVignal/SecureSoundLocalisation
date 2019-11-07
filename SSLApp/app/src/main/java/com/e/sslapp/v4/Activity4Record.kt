package com.e.sslapp.v4

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
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
import com.beust.klaxon.*
import com.e.sslapp.customElements.*
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity4_record.*

class Activity4Record : AppCompatActivity() {

    // ------------------------------------------------------------
    //                           Static object
    // ------------------------------------------------------------

    companion object {

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
    // ----------
    private var connected: Boolean = false
    // ----------
    private var recordStart: Long? = null
    private var recordDuration: Long? = null

    private var accepted: Boolean? = null


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
        setContentView(R.layout.activity4_record)

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

        button_connect_bluetooth.setOnClickListener{
            // val time = Calendar.getInstance().timeInMillis
            // Log.i("time", "Time: $time")
            changeActivity(Activity4ConnectBluetooth::class.java)
        }

        button_connection.setOnClickListener{
            if(connected){
                button_connection.text = "Start connection"
                connected = false
                stopConnection()
            } else {
                button_connection.text = "Stop connection"
                connected = true
                graph_waveform_recorded.removeAllSeries()
                startConnection()
                socket?.let{itSocket ->
                    sendMessage(itSocket, "device")
                }
                readTriggerMessage()
                startRecording()
            }
        }

        // If no bluetooth device connected, ask for connection:
        if(connectedBluetoothDevice == null){
            changeActivity(Activity4ConnectBluetooth::class.java)
        } else {
            text_paired_device.text = "${connectedBluetoothDevice?.name} - ${connectedBluetoothDevice?.address}"
        }
    }

    private fun getAllIntent() {
        // To get the intent (variables from the previous activity)
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
            setTheme(R.style.LightTheme
            )
        }
        if (!onCreate) {      // To avoid infinite loops
            changeActivity(Activity4Record::class.java)
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
        val activity = menu?.findItem(R.id.activity_record)
        activity?.title = "-> Record <-"
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
                changeActivity(Activity4Bluetooth::class.java)
                return true
            }
            R.id.activity_record -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity record pressed")
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
            R.id.activity_speaker -> {
                if (debug) {
                    Log.d("onOptionsItemSelected", "activity speaker pressed")
                }
                changeActivity(Activity4Speaker::class.java)
                return true
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
        intent.putExtra("previousActivity", "Record")
        // ----- Start activity -----
        startActivity(intent)
    }

    // ------------------------------ Helper ------------------------------

    inline fun <reified T> listToArray(list: List<*>): Array<T> {
        // Create a list from an array
        return (list as List<T>).toTypedArray()
    }


    // ------------------------------ Audio ------------------------------


    private fun startRecording() {
        text_view_state.text = "Wait to record.."
        if (debug) {
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("startRecording", "Button start pressed at $currentTime")
        }
        if (!isRecording) {
            recordStart?.let{itRecordStart ->
                var recordStartOffset = itRecordStart - Calendar.getInstance().timeInMillis
                recordDuration?.let{itRecordDuration ->
                    text_view_state.text = "Will record in ${recordStartOffset/1000}s"
                    val stopTime = itRecordStart + itRecordDuration
                    if (debug) {
                        Log.d(
                            "startRecording",
                            "offset : $recordStartOffset - startTime : $itRecordStart - duration : $itRecordDuration - stopTime : $stopTime"
                        )
                    }
                    try {
                        prepareRecorder()
                        recorder?.let { r ->
                            val handler = Handler()
                            r.startRecording()
                            recordStartOffset = itRecordStart - Calendar.getInstance().timeInMillis
                            handler.postDelayed({ getAudioData(stopTime) }, recordStartOffset)
                        }

                    } catch (e: IllegalStateException) {
                        Log.e("startRecording", "Can't start recording", e)
                    } catch (e: IOException) {
                        Log.e("startRecording", "Can't start recording", e)
                    }

                }
            }
        } else {
            Toast.makeText(this, "You are already recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun prepareRecorder() {
        recordedSound = ArrayList<Short>()      // Reset the recorded Sound
        isRecording = true
        Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
        text_view_state.text = "Recording..."
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            recorderSampleRate, recorderChannels,
            recorderAudioEncoding, bufferElements2Rec * bytesPerElement
        )
    }

    private fun getAudioData(stopDate: Long) {
        text_view_state.text = "Recording.."
        // Write the output audio in byte
        val sData = ShortArray(bufferElements2Rec)
        // val timeBeforeWile = Calendar.getInstance().timeInMillis
        // Log.d("getAudioData", "before while data: ${timeBeforeWile}")
        while (stopDate > Calendar.getInstance().timeInMillis) {
            // gets the voice output from microphone to byte format
            recorder?.read(sData, 0, bufferElements2Rec)
            sData.forEach { recordedSound?.add(it) }
        }
        // val timeAfterWile = Calendar.getInstance().timeInMillis
        // Log.d("getAudioData", "after while data: ${timeAfterWile}")
        // Log.d("getAudioData", "Duration : ${timeAfterWile - timeBeforeWile}")
        if (debug) {
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("getAudioData", "stop recording at $currentTime")
        }
        Log.d("getAudioData", "before while data: ${Calendar.getInstance().timeInMillis}")
        stopRecording()
    }

    // ------------------------------ Stop Recording ------------------------------

    private fun stopRecording() {
        text_view_state.text = "Cleaning recording ..."
        // stops the recording activity
        if (isRecording) {
            isRecording = false
            recorder?.let { r ->
                r.stop()
                r.release()
            }
            recorder = null
            recordingThread = null
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()

            // ----- Do the computation with the recordedSound ----
            cleanRecordedSound()        // Clean it
            updateGraphRecorder()       // Plot it
            text_view_state.text = "Press Start to record"
        } else {
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
        sendRecord()
        readAnswerMessage()
    }

    private fun cleanRecordedSound() {
        if(debug){
            Log.d(
                "cleanRecordedSound",
                "Length of the raw recording ${recordedSound?.size}"
            )
        }
        recordedSound?.let {
            // Check number of zeros at the beginning
            var nbZerosBeginning = 0
            for (i in 0 until it.size) {
                if (it[i].toInt() == 0) {
                    nbZerosBeginning++
                } else {
                    break
                }
            }
            // Check number of zeros at the beginning
            var nbZerosEnding = 0
            for (i in 0 until it.size) {
                if (it[i].toInt() == 0) {
                    nbZerosEnding++
                } else {
                    break
                }
            }
            // Remove it
            if (nbZerosBeginning < it.size - nbZerosEnding){
                recordedSound = ArrayList(it.subList(nbZerosBeginning, it.size - nbZerosEnding))
            }

            // Log it
            if (debug) {
                Log.d(
                    "cleanRecordedSound",
                    "Number of zeros at the: --  beginning : $nbZerosBeginning = ${nbZerosBeginning.toDouble() / recorderSampleRate.toDouble()} -- ending : $nbZerosEnding = ${nbZerosEnding.toDouble() / recorderSampleRate.toDouble()}"
                )
                Log.d(
                    "cleanRecordedSound",
                    "Length of the cleaned recording ${recordedSound?.size}"
                )
            }
            // Basically : there were just 1024 values==0 at the beginning and the ending of the recorded sound = size of a buffer
        }
    }

    private fun updateGraphRecorder() {
        // Used to plot the recorded Sound
        recordedSound?.let {
            // Create the DataPoint
            val dataPoints: List<DataPoint> = it.mapIndexed { index, sh ->
                DataPoint(
                    index.toDouble() / recorderSampleRate.toDouble(),
                    sh.toDouble()
                )
            }
            val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
            val series = LineGraphSeries<DataPoint>(dataPointsArray)

            graph_waveform_recorded.removeAllSeries()
            graph_waveform_recorded.addSeries(series)
            graph_waveform_recorded.setTitle("Recorded")
            graph_waveform_recorded.getViewport().setScalable(true)
        }
    }

    // ------------------------------ Bluetooth ------------------------------
    private fun startConnection() {
        text_view_state.text = "Initialazing connection..."
        try{
            socket = connectedBluetoothDevice?.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            Toast.makeText(this, "Connection started", Toast.LENGTH_SHORT).show()
        } catch(e: java.io.IOException) {
            Log.e("start connect", "Couldn't start the connection", e)
        }
    }

    private fun readTriggerMessage(){
        text_view_state.text = "Getting Triggering Messages..."
        try {
            socket?.let{itSocket ->
                val message = readMessage(itSocket)
                val messageJSON = Klaxon().parse<BluetoothRecordTrigger>(message)
                recordStart = messageJSON?.start
                recordDuration = messageJSON?.duration
                text_start.text = recordStart.toString()
                text_duration.text = recordDuration.toString()
            }
        } catch (e: KlaxonException){
            Log.e("readTriggerData", "Cannot parse the data", e)
            text_start.text = e.toString()
            text_duration.text = e.toString()
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
        text_view_state.text = "Press START CONNECTION"
    }


    private fun sendRecord() {
        text_view_state.text = "Sending Record ..."
        recordedSound?.let{itRecordedSound ->
            val recordedSoundString = Klaxon().toJsonString(
                BluetoothRecord(data=itRecordedSound)
            )
            text_record_sent.text = itRecordedSound.toString()
            socket?.let{itSocket ->
                sendMessage(itSocket, recordedSoundString)
            }
            Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
        }

    }

    private fun readAnswerMessage(){
        text_view_state.text = "Reading Acceptation Answer..."
        try{
            socket?.let{itSocket ->
                val message = readMessage(itSocket)
                val messageJSON = Klaxon().parse<bluetoothRecordAnswer>(message)
                accepted = messageJSON?.accepted
                accepted?.let{it->
                    if(it){
                        text_answer_accepted.text = "Accepted"
                        text_answer_accepted.setTextColor(Color.GREEN)
                    } else {
                        text_answer_accepted.text = "Rejected"
                        text_answer_accepted.setTextColor(Color.RED)
                    }
                }

            }
        } catch (e: KlaxonException){
            Log.e("readAnswerData", "Cannot parse the data", e)
            text_answer_accepted.text = e.toString()
            text_answer_accepted.setTextColor(Color.YELLOW)

        }
        text_view_state.text = "Press STOP CONNECTION"
    }

}





