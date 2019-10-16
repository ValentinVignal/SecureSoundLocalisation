package com.e.sslapp.v3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaRecorder
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
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.*
import kotlin.collections.ArrayList
import java.io.*
import java.lang.Math.*
import android.util.Log
import com.e.sslapp.v1.Activity1Manual
import com.e.sslapp.v2.Activity2Manual
import com.e.sslapp.R
import java.util.Calendar
import android.os.Handler
import kotlinx.android.synthetic.main.activity3_handler.*
import kotlinx.android.synthetic.main.activity3_manual.button_start_recording
import kotlinx.android.synthetic.main.activity3_manual.button_stop_recording
import kotlinx.android.synthetic.main.activity3_manual.switch_debug
import kotlinx.android.synthetic.main.activity3_manual.switch_save_record
import kotlinx.android.synthetic.main.activity3_manual.text_view_state


class Activity3Handler : AppCompatActivity() {

    // ------------------------------------------------------------
    //                           Static object
    // ------------------------------------------------------------

    companion object {

        // --------------------
        //      Attributs
        // --------------------

        var debug:Boolean = false // Use to debug (and for example print in the terminal)

        // --------------------
        //       Methods
        // --------------------

        fun newRecordPath(rootDirectory: File): String{
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
    private var saveRecord: Boolean = false
    private var mSaveRecord: Boolean = false // Save the state of the save_record switch at the end of the recording

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
    private var sentSound: ArrayList<Double>? = null      // The sound sent bu the Central Unit
    private var convolutedSound: ArrayList<Double>? = null      // The convolution between recordedSound and sentSound


    // ------------------------------------------------------------
    //                           Methods
    // ------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {

        // --------------------
        // Called at the creation
        // --------------------

        // -------------------- Set what needs to be set while debug --------------------
        changeTheme(debug, onCreate=true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity3_handler)

        // ---------- Handle Toolbar ----------
        toolbar = findViewById(R.id.activity_toolbar)
        setSupportActionBar(toolbar)
        val actionBar = supportActionBar
        actionBar?.title = "SSL"
        actionBar?.subtitle = "SecureSoundLocalisation - v3.0"
        actionBar?.elevation = 4.0F


        // ---------- Check the permission ----------
        checkPermission()

        // ----- Create the directory if it doesn't exist -----
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }

        // ----- Compute the supposed sent sound by the Central Unit -----
        //recreateSentSound()

        // -------------------- Call when Start button is pressed --------------------
        button_start_recording.setOnClickListener {
            if (checkPermission()){
                // We can start the recording
                startRecording()
            }
        }

        // -------------------- Call when Debug Switch changes  --------------------
        switch_debug.isChecked = debug
        switch_debug.setOnCheckedChangeListener { buttonView, isChecked ->
            changeTheme(isChecked)
        }

        // -------------------- Call when Save Record Switch changes  --------------------
        switch_save_record.isChecked = saveRecord
        switch_save_record.setOnCheckedChangeListener { buttonView, isChecked ->
            saveRecord = isChecked
        }
    }

    private fun checkPermission(): Boolean{
        val isNotChecked =(ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        )
        if (isNotChecked){
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
        return !isNotChecked
    }

    private fun changeTheme(onDebug:Boolean, onCreate:Boolean=false){
        if (onDebug) {
            debug = true
            //global_layout.setBackgroundColor(Color.rgb(240, 240, 240))
            setTheme(R.style.DarkTheme)

        } else {
            debug = false
            //global_layout.setBackgroundColor(Color.WHITE)
            setTheme(R.style.LightTheme)
        }
        if(!onCreate){      // To avoid infinite loops
            val intent = Intent(this, Activity3Handler::class.java)
            //intent.putExtra("debug", debug)
            startActivity(intent)
        }
    }

    // ------------------------------ Menu ------------------------------

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu3_toolbar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle presses on the action bar menu items
        when (item.itemId) {
            // -------------------- Version menu --------------------
            R.id.version_1_0 -> {
                if (debug){
                    println("v1 pressed")
                }
                val intent = Intent(this, Activity1Manual::class.java)
                startActivity(intent)
                return true
            }
            R.id.version_2_0 -> {
                if(debug){
                    println("v2 pressed")
                }
                val intent = Intent(this, Activity2Manual::class.java)
                startActivity(intent)
                return true
            }
            R.id.version_3_0 -> {
                if(debug){
                    println("v3 pressed")
                }
                return true
            }
            // -------------------- Activity menu --------------------
            R.id.activity_manual -> {
                if(debug){
                    Log.d("onOptionsItemSelected", "activity manual pressed")
                }
                val intent = Intent(this, Activity3Manual::class.java)
                startActivity(intent)
                return true
            }
            R.id.activity_handler -> {
                if(debug){
                    Log.d("onOptionsItemSelected", "activity handler pressed")
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
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

    // ------------------------------ Start Recording ------------------------------

    private fun startRecording() {
        if(debug){
            // val currentDate = LocalDateTime.now()
            // var milliseconds = currentDate.getTime()
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("startRecording","Button start pressed at $currentTime")
        }
        if (!isRecording) {
            val startOffset = floor(form_offset.text.toString().toDouble() * 1000).toLong()
            val startTime = Calendar.getInstance().timeInMillis + startOffset
            val duration = floor(form_duration.text.toString().toDouble() * 1000).toLong()
            val stopTime = startTime + duration
            if(debug){
                Log.d("startRecording","offset : $startOffset - startTime : $startTime - duration : $duration - stopTime : $stopTime")
            }
            try {
                prepareRecorder()
                recorder?.let { r ->
                    /*
                    recordingThread =
                        Thread(Runnable { writeAudioDataToFile() }, "AudioRecorder Thread")
                    r.startRecording()
                    recordingThread?.start()
                     */
                    val handler = Handler()
                    r.startRecording()
                    handler.postDelayed({ writeAudioDataToFile(stopTime) }, startOffset)
                }

            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "You are already recording", Toast.LENGTH_SHORT).show()

        }
    }

    private fun prepareRecorder(){
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

    private fun writeAudioDataToFile(stopDate:Long) {
        // Write the output audio in byte
        if(debug){
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("writeAudioDataToFile", "start recording at $currentTime")
        }

        val sData = ShortArray(bufferElements2Rec)

        if(debug){
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("writeAudioDataToFile", "Just before while at $currentTime")
        }
        while (stopDate > Calendar.getInstance().timeInMillis) {
            // gets the voice output from microphone to byte format

            recorder?.read(sData, 0, bufferElements2Rec)
            // val iData: IntArray = IntArray(sData.size){ sData[it].toInt() }
            sData.forEach { recordedSound?.add(it) }
            if (debug) {
                println(
                    "Short writing to file${Arrays.toString(sData.sliceArray(1..10))}..." +
                            "${sData.takeLast(10)} -- size : ${sData.size}"
                )
                println("Size of ArrayList: ${recordedSound?.size} -- ${recordedSound?.takeLast(10)}")
            }
        }
        if(debug){
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("writeAudioDataToFile", "stop recording at $currentTime")
        }
        stopRecording()
    }

    // ------------------------------ Stop Recording ------------------------------

    private fun stopRecording() {
        // stops the recording activity
        if (debug){
            val currentTime = Calendar.getInstance().timeInMillis
            Log.d("stopRecording", "In function at time $currentTime")
        }
        if (isRecording) {
            isRecording = false
            if (null != recorder) {
                isRecording = false
                recorder?.let { r ->
                    r.stop()
                    r.release()
                }
                recorder = null
                recordingThread = null
                if(mSaveRecord){
                    Toast.makeText(this, "Recording saved in $recordPath", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
                }
                text_view_state.text = "Press Start to record"

                // ----- Do the computation with the recordedSound -----
                updateGraphRecorder()       // Plot it
                //computeConvolutedSound()    // Compute the convolution with the sentSound
                //updateGraphConvolutedSound()        // Plot the convoluted Sound
            }
        } else {
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
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
            val series = LineGraphSeries<DataPoint>( dataPointsArray )

            graph_waveform_recorded.removeAllSeries()
            graph_waveform_recorded.addSeries(series)
            graph_waveform_recorded.setTitle("Recorded")
            graph_waveform_recorded.getViewport().setScalable(true)
        }
    }

}





