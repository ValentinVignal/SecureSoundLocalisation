package com.e.sslapp.v4

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.AudioRecord
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import kotlin.collections.ArrayList
import java.io.*
import android.util.Log
import android.view.View
import android.widget.*
import com.e.sslapp.v1.Activity1Manual
import com.e.sslapp.v2.Activity2Manual
import com.e.sslapp.v3.Activity3Handler
import com.e.sslapp.R
import kotlinx.android.synthetic.main.activity4_connect_bluetooth.*

// import com.e.sslapp.customElements.BluetoothReceiver


class Activity4ConnectBluetooth : AppCompatActivity() {

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

    private var previousActivity: String? = "Bluetooth"

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

    // Paired
    private var arrayListPaired: ArrayList<String> = ArrayList<String>()
    private var arrayAdapterPaired: ArrayAdapter<String>? = null
    private var arrayListPairedBluetoothDevices: ArrayList<BluetoothDevice>? = null

    // Found
    private var isScanning: Boolean = false
    private var foundBluetoothDevices: ArrayList<BluetoothDevice>? = null
    private var bluetoothReceiver: BroadcastReceiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action){
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                device?.let{
                    foundBluetoothDevices?.add(device)
                    showfoundBluetoothDevices()
                }
            }
        }
    }

    // connected
    private var connectedBluetoothDevice: BluetoothDevice? = null


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
        setContentView(R.layout.activity4_connect_bluetooth)

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

        // ---------- Buttons ----------
        button_refresh_paired.setOnClickListener{
            refreshBluetooth()
        }

        button_scan_nearby.setOnClickListener{
            if(isScanning){
                isScanning = false
                button_scan_nearby.text = "Scan Devices"
                stopScan()
                text_view_state.text = "Choose a device to connect to"
            } else {
                isScanning = true
                button_scan_nearby.text = "Stop Scan"
                text_view_state.text = "Scanning..."
                makeDiscoverable()
                startScan()
            }
        }
        listview_paired_devices.setOnItemClickListener { parent, view, position, id ->
            arrayListPairedBluetoothDevices?.let{
                connectedBluetoothDevice = it[position]
                activityBack()
            }
        }
    }

    private fun getAllIntent() {
        val intent = this.intent
        debug = intent.getBooleanExtra("debug", debug)
        saveRecord = intent.getBooleanExtra("saveRecord", saveRecord)
        //connectedBluetoothDevice = intent.getParcelableExtra("connectedBluetoothDevice")
        previousActivity = intent.getStringExtra("previousActivity")
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
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_ADMIN
        ) != PackageManager.PERMISSION_GRANTED
                )
        if (isNotChecked) {
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
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
            changeActivity(Activity4ConnectBluetooth::class.java)
        }
    }

    private fun initBluetooth(){
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Paired devices
        resetArrayListPaired()
        arrayListPairedBluetoothDevices = ArrayList<BluetoothDevice>()
        refreshBluetooth()

        // Found devices
        foundBluetoothDevices = ArrayList<BluetoothDevice>()

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

    private fun activityBack(){
        when(previousActivity){
            "Manual" -> {
                changeActivity(Activity4Manual::class.java)
            }
            "Handler" -> {
                changeActivity(Activity4Handler::class.java)
            }
            "Bluetooth" -> {
                changeActivity(Activity4Bluetooth::class.java)
            }
        }

    }

    private fun changeActivity(activity: Class<*>) {
        val intent = Intent(this, activity)
        // ----- Put Extra -----
        intent.putExtra("debug", debug)     // Debug value
        intent.putExtra("saveRecord", saveRecord)
        intent.putExtra("connectedBluetoothDevice", connectedBluetoothDevice)
        intent.putExtra("previousActivity", "ConnectBlueTooth")
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

    // ---------- Paired Devices ----------
    private fun resetArrayListPaired(){
        arrayListPaired = ArrayList<String>()
        arrayAdapterPaired = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, arrayListPaired)
        val listViewPaired = findViewById<ListView>(R.id.listview_paired_devices)
        listViewPaired.adapter = arrayAdapterPaired
    }

    private fun refreshBluetooth() {
        resetArrayListPaired()
        arrayListPairedBluetoothDevices = ArrayList<BluetoothDevice>()
        listview_paired_devices.clearChoices()
        val pairedDevice: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevice?.let{
            if (it.isNotEmpty()) {
                for (device in pairedDevice) {
                    arrayListPaired?.add("${device.name} - ${device.address}")
                    arrayListPairedBluetoothDevices?.add(device)
                }
            }
            if(debug){
                Log.d("refreshBluetooth", "array List paired : $arrayListPaired")
            }
        }
        showArrayListPaired()
        Toast.makeText(this, "Paired Bluetooth devices refreshed", Toast.LENGTH_SHORT).show()
    }

    private fun showArrayListPaired(){
        var s = ""
        arrayListPaired?.let{
            for(m in it){
                s += m + ",\n"
            }
        }
        text_paired_devices.text = s
        Log.d("showArrayListPared", "$arrayAdapterPaired")
        arrayAdapterPaired?.notifyDataSetChanged()

    }

    // ---------- Scan Devices ----------

    private fun makeDiscoverable() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(discoverableIntent)
        Log.i("Log", "Discoverable ")
    }

    private fun startScan(){
        Toast.makeText(this, "Start Scan", Toast.LENGTH_SHORT).show()
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        foundBluetoothDevices = ArrayList<BluetoothDevice>()
        registerReceiver(bluetoothReceiver, filter)
        bluetoothAdapter?.startDiscovery()
    }

    private fun stopScan(){
        unregisterReceiver(bluetoothReceiver)
        bluetoothAdapter?.cancelDiscovery()
        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showfoundBluetoothDevices(){
        var s: String = ""
        foundBluetoothDevices?.let{
            for(d in it){
                s += "${d.name} - ${d.address},\n"
            }
        }
        text_found_devices.text = s
        Log.d("showFound", "$s $foundBluetoothDevices")
    }



}





