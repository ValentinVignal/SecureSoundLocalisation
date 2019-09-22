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
    private var debug: Boolean = true       // Use to debug (and for example print in the terminal)
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
        // Call at the creation
        // --------------------
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main2)

        // ---------- Check the permission ----------
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
        // ----- Create the directory if it doesn't exist -----
        if (!rootDirectory.exists()) {
            rootDirectory.mkdirs()
        }

        // ----- Compute the supposed sent sound by the Central Unit -----
        recreateSentSound()
        switch_save_record.isChecked = saveRecord

        // -------------------- Set what needs to be set while debug --------------------
        if (debug) {
            switch_debug.isChecked = true
            global_layout.setBackgroundColor(Color.rgb(240, 240, 240))
            textview_sound_recorder_heading.setTextColor(Color.rgb(160, 52, 52))
        } else {
            switch_debug.isChecked = false
            global_layout.setBackgroundColor(Color.WHITE)
            textview_sound_recorder_heading.setTextColor(Color.BLACK)
        }

        // -------------------- Call when Start button is pressed --------------------
        button_start_recording.setOnClickListener {
            // If the we didn't allow
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
                // We can start the recording
                startRecording()
            }
        }

        // -------------------- Call when Stop button is pressed --------------------
        button_stop_recording.setOnClickListener {
            stopRecording()
        }


        // -------------------- Call when Debug Switch changes  --------------------
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

        // -------------------- Call when Save Record Switch changes  --------------------
        switch_save_record.setOnCheckedChangeListener { buttonView, isChecked ->
            saveRecord = isChecked
        }
    }


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

    private fun startRecording() {
        if (!isRecording) {
            try {
                recordedSound = ArrayList<Short>()      // Reset the recorded Sound
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

        val sData = ShortArray(bufferElements2Rec)

        var os: FileOutputStream? = null
        if(mSaveRecord){        // If the save record switch was ON at the beginning of the recording
            // ----- Find an untaken name for the file -----
            var i = 0
            mSaveRecord = saveRecord
            recordPath = rootDirectory.absolutePath + "/recording_$i.pcm"
            while (File(recordPath).exists()) {
                i += 1
                recordPath = rootDirectory.absolutePath + "/recording_$i.pcm"
            }
            // ----- Create the output Stream -----
            try {
                os = FileOutputStream(recordPath)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }

        while (isRecording) {
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
            if(mSaveRecord){
                try {
                    // // writes the data to file from buffer
                    // // stores the voice buffer
                    val bData = short2byte(sData)
                    os!!.write(bData, 0, bufferElements2Rec * bytesPerElement)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        if (mSaveRecord){
            try {
                os!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    inline fun <reified T> listToArray(list: List<*>): Array<T> {
        // Create a list from an array
        return (list as List<T>).toTypedArray()
    }

    private fun stopRecording() {
        // stops the recording activity
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
                computeConvolutedSound()    // Compute the convolution with the sentSound
                updateGraphConvolutedSound()        // Plot the convoluted Sound
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

    private fun computeConvolutedSound() {
        // Used to create the convoluted sound
        convolutedSound = ArrayList<Double>()
        convolutedSound?.let { itConvSound ->
            recordedSound?.let { itLastRecord ->
                sentSound?.let { itSendedSound ->
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
                }
            }
        }
    }

    private fun updateGraphConvolutedSound() {
        convolutedSound?.let{itConvolutedSound ->
            // --- Get the index of the max of the convoluted sound ---
            var indexMax: Int = 0
            var max: Double = itConvolutedSound[0]
            for (i in 1..(itConvolutedSound.size - 1)){
                if (max < itConvolutedSound[i]){
                    max = itConvolutedSound[i]
                    indexMax = i
                }
            }
            if (debug){
                println("In the itConvoluted sound --> Index: $indexMax, Max: $max")
            }
            // Create the series to plot the red marker whee there is the maximum
            val dataPointMarker = arrayOfNulls<DataPoint>(2)
            dataPointMarker[0] = DataPoint(indexMax.toDouble() / recorderSampleRate, (- max))
            dataPointMarker[1] = DataPoint(indexMax.toDouble() / recorderSampleRate, max)
            val seriesMarker = LineGraphSeries<DataPoint>(dataPointMarker)
            seriesMarker.setColor(Color.RED)

            // Create the series to plot the convoluted sound
            val dataPoints: List<DataPoint> = itConvolutedSound.mapIndexed { index, sh ->
                DataPoint( index.toDouble() / recorderSampleRate.toDouble(), sh )
            }
            val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
            val series = LineGraphSeries<DataPoint>( dataPointsArray )

            // ----- Plot it !!! -----
            graph_waveform_convoluted.removeAllSeries()
            graph_waveform_convoluted.addSeries(series)
            graph_waveform_convoluted.addSeries(seriesMarker)
            graph_waveform_convoluted.setTitle("Convoluted")
            graph_waveform_convoluted.getViewport().setScalable(true)
        }
    }

    private fun recreateSentSound() {
        val duration = 0.25     // In second
        val f = 440.0           // In Hertz
        val nbPoints = recorderSampleRate * duration
        if (debug){
            println("Number of point in the sentSound: ${nbPoints.toInt()}")
        }
        sentSound = ArrayList<Double>()
        sentSound?.let {
            for (i in 1..(duration * recorderSampleRate).toInt()) {
                it.add(sin(2 * PI * f * i / recorderSampleRate))        // For now we send a sinus
            }
            if (debug){
                println("SendedSound (size: ${it.size}) -->${it.take(10)}...${it.takeLast(10)}")
            }
            updateGraphSent()
        }

    }

    private fun updateGraphSent() {
        sentSound?.let{itSentSound ->
            val dataPoints: List<DataPoint> = itSentSound?.mapIndexed { index, sh ->
                DataPoint( index.toDouble() / recorderSampleRate.toDouble(), sh )
            }
            val dataPointsArray: Array<DataPoint> = listToArray<DataPoint>(dataPoints)
            val series = LineGraphSeries<DataPoint>( dataPointsArray )

            // ----- PLot it -----
            graph_waveform_sent.removeAllSeries()
            graph_waveform_sent.addSeries(series)
            graph_waveform_sent.setTitle("Sended")
            graph_waveform_sent.getViewport().setScalable(true)
            graph_waveform_sent.getGridLabelRenderer().setVerticalLabelsVisible(false)
            graph_waveform_sent.getGridLabelRenderer().setHorizontalLabelsVisible(false)

        }
    }

}

