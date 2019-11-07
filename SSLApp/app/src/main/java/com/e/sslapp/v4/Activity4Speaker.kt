package com.e.sslapp.v4

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlin.collections.ArrayList
import java.io.*
import android.util.Log
import android.widget.Toast
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import com.e.sslapp.v1.Activity1Manual
import com.e.sslapp.v2.Activity2Manual
import com.e.sslapp.v3.Activity3Handler
import com.e.sslapp.R
import com.e.sslapp.customElements.*
import kotlinx.android.synthetic.main.activity4_speaker.*
import kotlinx.android.synthetic.main.activity4_speaker.button_connect_bluetooth
import kotlinx.android.synthetic.main.activity4_speaker.button_connection
import java.util.*


class Activity4Speaker : AppCompatActivity() {

    // ------------------------------------------------------------
    //                           Static object
    // ------------------------------------------------------------


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

    // ---------- Variables for AudioRecord ----------
    private val sampleRate: Int = 8000  // For emulator, put 44100 for real phone

    // ---------- Bluetooth ----------
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedBluetoothDevice: BluetoothDevice? = null
    private var uuid: UUID = UUID.fromString("ae465fd9-2d3b-a4c6-4385-ea69b4c1e23c")
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    // ----------
    private var connected: Boolean = false

    var speakerNumber: Int = 1
    var positionX: Float? = null
    var positionY: Float? = null

    var threadBT: ThreadSpeakers? = null

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
        setContentView(R.layout.activity4_speaker)

        // ---------- Handle Toolbar ----------
        toolbar = findViewById(R.id.activity_toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.title = "SSL"
        actionBar?.subtitle = "SecureSoundLocalisation - v4.0"
        actionBar?.elevation = 4.0F

        // ---------- Check the permission ----------
        checkPermission()

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
                threadBT?.interrupt()
                stopConnection()
            } else {
                button_connection.text = "Stop connection"
                connected = true
                graph_waveform_sound.removeAllSeries()
                startConnection()
                readPositionMessage()

                //speaker()
                socket?.let{
                    threadBT = ThreadSpeakers(it, sampleRate,this)
                    threadBT?.start()
                }
            }
        }

        // If no bluetooth device connected, ask for connection:
        if(connectedBluetoothDevice == null){
            changeActivity(Activity4ConnectBluetooth::class.java)
        } else {
            text_paired_device.text = "${connectedBluetoothDevice?.name} - ${connectedBluetoothDevice?.address}"
        }
    }

    private fun getAllIntent(){
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
        ) != PackageManager.PERMISSION_GRANTED
                )
        if (isNotChecked) {
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
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
            changeActivity(Activity4Speaker::class.java)
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

    private fun initiateMenuItems(menu: Menu?){
        // ----- Activities -----
        val activity = menu?.findItem(R.id.activity_speaker)
        activity?.title = "-> Speaker <-"
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
                /*
                val intent = Intent(this, Activity3Manual::class.java)
                startActivity(intent)
                 */
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
            R.id.activity_bluetooth-> {
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
                changeActivity(Activity4Record::class.java)
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
                if(saveRecord){
                    saveRecord = false
                    item.title = "Save Record: OFF"
                } else {
                    saveRecord = true
                    item.title = "Save Record: ON"
                }
                if(debug){
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
                if(debug){
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


    private fun changeActivity(activity: Class<*>){
        val intent = Intent(this, activity)
        // ----- Put Extra -----
        intent.putExtra("debug", debug)     // Debug value
        intent.putExtra("saveRecord", saveRecord)
        intent.putExtra("previousActivity", "Speaker")
        // ----- Start activity -----
        startActivity(intent)
    }


    // ------------------------------ Helper ------------------------------

    inline fun <reified T> listToArray(list: List<*>): Array<T> {
        // Create a list from an array
        return (list as List<T>).toTypedArray()
    }

    // ------------------------------ Create sound ------------------------------

    fun updateGraphSound(sound: ArrayList<Short>) {
        runOnUiThread(Runnable {
            // Used to plot the recorded Sound
            // Create the DataPoint
            val dataPoints: List<DataPoint> = sound.mapIndexed { index, sh ->
                DataPoint(
                    index.toDouble() / sampleRate.toDouble(),
                    sh.toDouble()
                )
            }
            val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
            val series = LineGraphSeries<DataPoint>(dataPointsArray)

            graph_waveform_sound.removeAllSeries()
            graph_waveform_sound.addSeries(series)
            graph_waveform_sound.setTitle("Recorded")
            graph_waveform_sound.getViewport().setScalable(true)
        })
    }

    // ------------------------------ Bluetooth ------------------------------

    private fun getUUID(){
        val s = "ae465fd9-2d3b-a4c6-4385-ea69b4c1e23c" //${speakerNumber - 1}"
        uuid = UUID.fromString(s)
    }

    private fun startConnection() {
        text_view_state.text = "Initialazing connection..."
        try{
            println("before the soeaker number")
            speakerNumber = form_speaker_number.text.toString().toInt()
            println("before the UUID")
            getUUID()
            println("Got the UUID")
            socket = connectedBluetoothDevice?.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            Toast.makeText(this, "Connection started", Toast.LENGTH_SHORT).show()
        } catch(e: java.io.IOException) {
            Log.e("start connect", "Couldn't start the connection", e)
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


    private fun readPositionMessage(){
        Log.d("ReadPositionMessage", "in function")
        text_view_state.text = "Getting the position..."
        try {
            socket?.let{itSocket ->
                val message = readMessage(itSocket)
                val messageJSON = Klaxon().parse<BluetoothSpeakerPosition>(message)
                positionX = messageJSON?.x
                positionY = messageJSON?.y
                Log.d("readPositionMessage", "X $positionX, Y $positionY")
                text_position_x.text = positionX.toString()
                text_position_y.text = positionY.toString()
            }
        } catch (e: KlaxonException){
            Log.e("readTriggerData", "Cannot parse the data", e)
            text_position_x.text = e.toString()
            text_position_y.text = e.toString()
        }
    }


    // ------------------------------ Set texts ------------------------------

    fun setTextViewState(text: String){
        runOnUiThread(Runnable {
            text_view_state.text = text
        })
    }

    fun setTextStartPlay(text: String){
        runOnUiThread(Runnable{
            text_start_play.text = text
        })
    }

    fun setTextStartPlayTrue(text: String){
        runOnUiThread(Runnable{
            text_start_play_true.text = text
        })
    }


}





