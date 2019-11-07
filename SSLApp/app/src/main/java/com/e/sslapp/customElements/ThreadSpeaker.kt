package com.e.sslapp.customElements

import android.bluetooth.BluetoothSocket
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.beust.klaxon.Klaxon
import com.beust.klaxon.KlaxonException
import com.e.sslapp.v4.Activity4Speaker
import java.util.*
import kotlin.collections.ArrayList


class ThreadSpeakers(socket: BluetoothSocket, sampleRate: Int, activity:Activity4Speaker): Thread() {
    val activity = activity
    val sampleRate = sampleRate
    val socket = socket

    var running: Boolean = true
    var currentWorker: Thread? = null

    override fun run(){
        try{
            while(! interrupted()){
                println("is Alive")
                println("while running: $running")
                currentWorker = ThreadSpeaker(socket, sampleRate, activity)
                currentWorker?.start()
                currentWorker?.let{itCurrentWorker ->
                    while(itCurrentWorker.isAlive){
                        sleep(1000)
                    }
                }
            }
        } catch (e: InterruptedException){
            currentWorker?.interrupt()
            currentThread().interrupt()
            Log.d("ThreadSpeakers", "interrupted")
        }
    }
}

class ThreadSpeaker(socket: BluetoothSocket, sampleRate: Int, activity:Activity4Speaker): Thread() {
    val activity = activity
    val sampleRate = sampleRate
    val socket = socket

    override fun run() {
        try{
            val (startPlay, soundToPlay) = readSoundMessage()
            startPlay?.let{itStartPlay ->
                soundToPlay?.let{itSoundToPlay ->
                    println("in both let")
                    activity.updateGraphSound(itSoundToPlay)
                    val datePlayed = playSound(itStartPlay, itSoundToPlay)
                    sendDatePlayed(datePlayed)
                }
            }
        } catch (e: InterruptedException){
            currentThread().interrupt()
            Log.d("ThreadSpeaker", "Interrupted")
        }
    }



    private fun readSoundMessage(): Pair<Long?, ArrayList<Short>?>{
        activity.setTextViewState("Getting the sound to play...")
        try {
            val message = readMessage(socket)
            val messageJSON = Klaxon().parse<BluetoothSpeakerSound>(message)
            val startPlay = messageJSON?.start
            val soundToPlay = messageJSON?.sound
            activity.setTextStartPlay(startPlay.toString())
            return Pair(startPlay, soundToPlay)
        } catch (e: KlaxonException){
            Log.e("readTriggerData", "Cannot parse the data", e)
            activity.setTextViewState(text = e.toString())
            return Pair(null, null)
        }
    }

    private fun playSound(startPlay: Long, soundToPlay:ArrayList<Short>): Long{
        activity.setTextViewState("Prepare for playing the sound...")
        soundToPlay.let{itSoundToPlay ->
            val createdSoundArray: ShortArray = ShortArray(itSoundToPlay.size){i ->
                itSoundToPlay[i].toShort()
            }
            val track = AudioTrack( AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, itSoundToPlay.size, AudioTrack.MODE_STATIC)
            track.write(createdSoundArray, 0, itSoundToPlay.size)
            Log.d("playSound", "startPlay $startPlay - currentDate ${Calendar.getInstance().timeInMillis}")
            val startOffset = startPlay - Calendar.getInstance().timeInMillis
            sleep(Math.max(startOffset, 0))
            val datePLayed = Calendar.getInstance().timeInMillis
            track.play()
            return datePLayed
        }
    }
    private fun sendDatePlayed(datePlayed: Long){
        activity.setTextViewState("Send the True Start Date")
        val startPlayTruthString = Klaxon().toJsonString(
            BluetoothSpeakerTrueStart(start=datePlayed)
        )
        activity.setTextStartPlayTrue(datePlayed.toString())
        try {
            socket.outputStream?.write(startPlayTruthString.toByteArray())
            socket.outputStream?.flush()
            Log.i("sendStartPlayTruth", "Sent")
        } catch (e: Exception) {
            Log.e("sendStartPlayTruth", "Cannot send", e)
        }
    }
}



fun readMessage(socket: BluetoothSocket): String{
    try{
        var available = socket.inputStream?.available()
        while(available == 0){
            Thread.sleep(200)
            available = socket.inputStream?.available()
            println()
        }
        var text = ""
        while (available != 0){
            available?.let{ itAvailable ->
                val bytes = ByteArray(itAvailable)
                Log.i("readMessage", "Reading")
                socket.inputStream?.read(bytes, 0, itAvailable)
                Log.i("readMessage", "InputStream ${socket.inputStream}")
                Log.i("readMessage", "available $itAvailable")
                text += String(bytes)
                Log.i("readMessage", "Message received: text $text")
                Thread.sleep(100)
                available = socket.inputStream?.available()
            }
        }
        return text
    } catch (e: java.lang.Exception){
        Log.e("readMessage", "Cannot read data", e)
        return e.toString()
    } catch (e: java.lang.NullPointerException){
        Log.e("readMessage", "Input Stream not available", e)
        return e.toString()
    }
}



