package com.e.sslapp.v4

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.AudioRecord
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlin.collections.ArrayList
import java.io.*
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.e.sslapp.v1.Activity1Manual
import com.e.sslapp.v2.Activity2Manual
import com.e.sslapp.v3.Activity3Handler
import com.e.sslapp.R
import kotlinx.android.synthetic.main.activity3_handler.*
import kotlinx.android.synthetic.main.activity3_handler.button_start_recording
import kotlinx.android.synthetic.main.activity3_handler.graph_waveform_recorded
import kotlinx.android.synthetic.main.activity4_play.*


class Activity4Play : AppCompatActivity() {

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

    // ---------- Handle the Sound ----------
    private var spinnerChoices: ArrayList<String> = ArrayList<String>()
    private var arrayAdapterSpinner: ArrayAdapter<String>? = null
    private var createdSound: ArrayList<Short>? = null     // The sound recorder by the phone


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
        setContentView(R.layout.activity4_play)

        // ---------- Handle Toolbar ----------
        toolbar = findViewById(R.id.activity_toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.title = "SSL"
        actionBar?.subtitle = "SecureSoundLocalisation - v4.0"
        actionBar?.elevation = 4.0F

        // ---------- Check the permission ----------
        checkPermission()

        // ---------- Spinner ----------
        initSpinnerSound()

        // -------------------- Call when Start button is pressed --------------------
        button_start_recording.setOnClickListener {
            if (checkPermission()) {
                // We can start the recording
                //startRecording()
            }
        }

        /*
        form_spinner_sound.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                getAllIntent()
            }
        }

         */
        form_spinner_sound.onItemSelectedListener = object: AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(debug){
                    Log.d("Spinner.onItemSelected", "position: $position")
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                form_spinner_sound.setSelection(0)
            }
        }

    }

    private fun getAllIntent(){
        val intent = this.intent
        debug = intent.getBooleanExtra("debug", debug)
        saveRecord = intent.getBooleanExtra("saveRecord", saveRecord)
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
            changeActivity(Activity4Play::class.java)
        }
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
        val activity = menu?.findItem(R.id.activity_play)
        activity?.title = "-> Play <-"
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

    // ------------------------------ Choose sound ------------------------------

    private fun initSpinnerSound(){
        spinnerChoices.add("sin")
        spinnerChoices.add("white noise")
        arrayAdapterSpinner = ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, spinnerChoices)
        val spinner = findViewById<Spinner>(R.id.form_spinner_sound)
        spinner.adapter = arrayAdapterSpinner
    }


    // ------------------------------ Stop Recording ------------------------------


    private fun updateGraphRecorder() {
        // Used to plot the recorded Sound
        createdSound?.let {
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

}





