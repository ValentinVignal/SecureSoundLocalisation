package com.e.sslapp

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
import android.media.AudioRecord
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.util.*
import kotlin.collections.ArrayList
import java.io.*
import java.lang.Math.*


class MainActivity2 : AppCompatActivity() {

    // ------------------------------------------------------------
    //                           Attributs
    // ------------------------------------------------------------

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
    private var sendedSound: ArrayList<Double>? = null
    private var convolutedSound: ArrayList<Double>? = null


    // ------------------------------------------------------------
    //                           Methods
    // ------------------------------------------------------------

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

        recreateSendedSound()

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
        switch_debug.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
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
                lastRecord = ArrayList<Short>()
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
                updateGraphRecorder()
                computeConvolutedSound()
            }
        } else {
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGraphRecorder() {
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
                dataPointsArray

            )
            graph_waveform_recorded.removeAllSeries()
            graph_waveform_recorded.addSeries(series)
            graph_waveform_recorded.setTitle("Record")
            graph_waveform_recorded.getViewport().setScalable(true)
        }
    }

    private fun computeConvolutedSound() {
        convolutedSound = ArrayList<Double>()
        convolutedSound?.let { itConvSound ->
            lastRecord?.let { itLastRecord ->
                sendedSound?.let { itSendedSound ->
                    val startOffset: Int = -floor(itSendedSound.size.toDouble() / 2).toInt()
                    val stopOffset: Int = ceil(itSendedSound.size.toDouble() / 2).toInt()
                    for (i in 0..(itLastRecord.size - 1)) {
                        var convValue: Double = 0.0
                        for (j in 0..(itSendedSound.size - 1)) {
                            if (j + i + startOffset < 0) {
                                continue
                            } else {
                                if (j + i + startOffset >= itLastRecord.size) {
                                    continue
                                } else {
                                    convValue += itLastRecord[i + j + startOffset] * itSendedSound[j]
                                }
                            }
                        }
                        itConvSound.add(convValue / itSendedSound.size)
                    }
                    updateGraphConvolutedSound(itConvSound)
                }
            }
        }
    }

    private fun updateGraphConvolutedSound(convolutedSound: ArrayList<Double>) {
        // Create the DataPoint

        var indexMax: Int = 0
        var max: Double = convolutedSound[0]
        for (i in 1..(convolutedSound.size - 1)){
            if (max < convolutedSound[i]){
                max = convolutedSound[i]
                indexMax = i
            }
        }
        if (debug){
            println("In the convoluted sound --> Index: $indexMax, Max: $max")
        }

        val dataPointMarker = kotlin.arrayOfNulls<DataPoint>(2)
        dataPointMarker[0] = DataPoint(indexMax.toDouble() / recorderSampleRate, (- max.toDouble()))
        dataPointMarker[1] = DataPoint(indexMax.toDouble() / recorderSampleRate, (max.toDouble()))
        var seriesMarker = LineGraphSeries<DataPoint>(dataPointMarker)
        seriesMarker.setColor(Color.RED)


        val dataPoints: List<DataPoint> = convolutedSound.mapIndexed { index, sh ->
            DataPoint(
                index.toDouble() / recorderSampleRate.toDouble(),
                sh
            )
        }
        val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
        val series = LineGraphSeries<DataPoint>( dataPointsArray )
        graph_waveform_convoluted.removeAllSeries()
        graph_waveform_convoluted.addSeries(series)
        graph_waveform_convoluted.addSeries(seriesMarker)
        graph_waveform_convoluted.setTitle("Convoluted")
        graph_waveform_convoluted.getViewport().setScalable(true)

    }

    private fun recreateSendedSound() {
        val duration = 0.25
        val f = 440.0
        val nbPoints = recorderSampleRate * duration
        println("nbPoints ${nbPoints.toInt()}")
        sendedSound = ArrayList<Double>()
        sendedSound?.let {
            for (i in 1..(duration * recorderSampleRate).toInt()) {
                it.add(sin(2 * PI * f * i / recorderSampleRate))
            }

            println("SendedSound: ${it.size}")
            updateGraphSended(it)
        }

    }

    private fun updateGraphSended(sended: ArrayList<Double>) {
        // Create the DataPoint


        val dataPoints: List<DataPoint> = sended.mapIndexed { index, sh ->
            DataPoint(
                index.toDouble() / recorderSampleRate.toDouble(),
                sh
            )
        }
        val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
        val series = LineGraphSeries<DataPoint>(
            dataPointsArray

        )
        graph_waveform_sended.removeAllSeries()
        graph_waveform_sended.addSeries(series)
        graph_waveform_sended.setTitle("Sended")
        graph_waveform_sended.getViewport().setScalable(true)
        graph_waveform_sended.getGridLabelRenderer().setVerticalLabelsVisible(false)
        graph_waveform_sended.getGridLabelRenderer().setHorizontalLabelsVisible(false)
    }

}

