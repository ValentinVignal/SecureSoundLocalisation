package com.e.audiorecorder26

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording: Boolean = false
    private var isInPause: Boolean = false
    private var recordPath: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            ActivityCompat.requestPermissions(this, permissions,0)
        }
        val rootDirectory = File(Environment.getExternalStorageDirectory().absolutePath + "/SSL")
        if (!rootDirectory.exists()){
            rootDirectory.mkdirs()
        }

        button_start_recording.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(this, permissions,0)
            } else {
                var i = 0
                recordPath = rootDirectory.absolutePath + "/recording_$i.mp3"
                while(File(recordPath).exists()){
                    i += 1
                    recordPath = rootDirectory.absolutePath + "/recording_$i.mp3"
                }
                mediaRecorder = MediaRecorder()
                mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder?.setOutputFile(recordPath)
                startRecording()
            }
        }

        button_stop_recording.setOnClickListener{
            stopRecording()
        }

        button_pause_recording.setOnClickListener {
            pauseRecording()
        }
    }

    private fun startRecording() {
        if(!isRecording){
            try {
                mediaRecorder?.prepare()
                mediaRecorder?.start()
                isRecording = true
                Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
                text_view_state.text = "Recording..."
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun pauseRecording() {
        if(isRecording) {
            if(!isInPause){
                Toast.makeText(this,"Pause!", Toast.LENGTH_SHORT).show()
                mediaRecorder?.pause()
                isInPause = true
                button_pause_recording.text = "Resume"
                text_view_state.text = "Recording in Pause"
            }else{
                resumeRecording()
            }
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun resumeRecording() {
        Toast.makeText(this,"Resume!", Toast.LENGTH_SHORT).show()
        mediaRecorder?.resume()
        button_pause_recording.text = "Pause"
        isInPause = false
        text_view_state.text = "Recording..."
    }

    private fun stopRecording(){
        if(isRecording){
            mediaRecorder?.stop()
            mediaRecorder?.release()
            Toast.makeText(this,"Recording saved in $recordPath", Toast.LENGTH_SHORT).show()
            isRecording = false
            button_pause_recording.text = "Pause"
            text_view_state.text = "Press Start to record"
        }else{
            Toast.makeText(this, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }
}
