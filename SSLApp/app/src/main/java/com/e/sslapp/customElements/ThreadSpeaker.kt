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

    var currentWorker: Thread? = null

    override fun run(){
        try{
            while(! interrupted()){
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
            activity.setTextStartPlayTrue("")
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
        sendMessage(socket, startPlayTruthString)
    }
}



