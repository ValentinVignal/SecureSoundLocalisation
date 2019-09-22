package com.e.sllapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main2.*
import java.io.File
import java.io.IOException
import android.media.AudioRecord
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity2 : AppCompatActivity() {

    // ---------- Debug options ----------
    private var debug: Boolean = true

    // ---------- State of the recorder ----------
    private var isRecording: Boolean = false
    private var isInPause: Boolean = false
    private var recordPath: String? = null

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
        File(Environment.getExternalStorageDirectory().absolutePath + "/SSL")
    private var lastRecord: ArrayList<Short>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, 0)
        }
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }

        button_start_recording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                val permissions = arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                ActivityCompat.requestPermissions(this, permissions, 0)
            } else {
                startRecording()
            }
        }

        button_stop_recording.setOnClickListener {
            stopRecording()
        }

        if (debug) {
            switch_debug.isChecked = true
            global_layout.setBackgroundColor(Color.rgb(240, 240, 240))
            textview_sound_recorder_heading.setTextColor(Color.rgb(160, 52, 52))
        } else {
            switch_debug.isChecked = false
            global_layout.setBackgroundColor(Color.WHITE)
            textview_sound_recorder_heading.setTextColor(Color.BLACK)
        }
        switch_debug.setOnCheckedChangeListener{ buttonView, isChecked ->
            if (isChecked){
                debug = true
                global_layout.setBackgroundColor(Color.rgb(240, 240, 240))
                textview_sound_recorder_heading.setTextColor(Color.rgb(160, 52, 52))
            } else {
                debug = false
                global_layout.setBackgroundColor(Color.WHITE)
                textview_sound_recorder_heading.setTextColor(Color.BLACK)
            }

        }
    }


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


    private fun startRecording() {
        if (!isRecording) {
            try {
                lastRecord = ArrayList<Short>()
                isRecording = true
                Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
                text_view_state.text = "Recording..."
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    recorderSampleRate, recorderChannels,
                    recorderAudioEncoding, bufferElements2Rec * bytesPerElement
                )

                recorder?.let { r ->
                    r.startRecording()
                    isRecording = true
                    recordingThread =
                        Thread(Runnable { writeAudioDataToFile() }, "AudioRecorder Thread")
                }
                recordingThread?.start()

            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "You are already recording", Toast.LENGTH_SHORT).show()

        }
    }

    private fun writeAudioDataToFile() {
        // Write the output audio in byte

        var i = 0
        recordPath = rootDirectory.absolutePath + "/recording_$i.pcm"
        while (File(recordPath).exists()) {
            i += 1
            recordPath = rootDirectory.absolutePath + "/recording_$i.pcm"
        }
        val sData = ShortArray(bufferElements2Rec)

        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(recordPath)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }

        while (isRecording) {
            // gets the voice output from microphone to byte format

            recorder?.read(sData, 0, bufferElements2Rec)
            // val iData: IntArray = IntArray(sData.size){ sData[it].toInt() }
            sData.forEach { lastRecord?.add(it) }
            if (debug) {
                println(
                    "Short writing to file${Arrays.toString(sData.sliceArray(1..10))}..." +
                            "${sData.takeLast(10)} -- size : ${sData.size}"
                )
                println("Size of ArrayList: ${lastRecord?.size} -- ${lastRecord?.takeLast(10)}")
            }
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                val bData = short2byte(sData)
                os!!.write(bData, 0, bufferElements2Rec * bytesPerElement)
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        try {
            os!!.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    inline fun <reified T> listToArray(list: List<*>): Array<T> {
        return (list as List<T>).toTypedArray()
    }

    private fun stopRecording() {
        // stops the recording activity
        if (isRecording) {
            if (null != recorder) {
                isRecording = false
                recorder?.let { r ->
                    r.stop()
                    r.release()
                }
                recorder = null
                recordingThread = null
                Toast.makeText(this, "Recording saved in $recordPath", Toast.LENGTH_SHORT).show()
                isRecording = false
                text_view_state.text = "Press Start to record"
                lastRecord?.let {
                    // Create the DataPoint

                    val dataPoints: List<DataPoint> = it.mapIndexed { index, sh ->
                        DataPoint(
                            index.toDouble() / recorderSampleRate.toDouble(),
                            sh.toDouble()
                        )
                    }
                    val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
                    val series = LineGraphSeries<DataPoint>(
                        /*
                        arrayOf<DataPoint>(
                            DataPoint(0.0, 1.0),
                            DataPoint(1.0, 5.0),
                            DataPoint(2.0, 3.0),
                            DataPoint(3.0, 2.0),
                            DataPoint(4.0, 6.0)
                        )

                         */
                        dataPointsArray

                    )
                    graph_waveform.addSeries(series)
                    graph_waveform.setTitle("Record")
                    graph_waveform.getViewport().setScalable(true)
                }
            }
        } else {
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }
}

