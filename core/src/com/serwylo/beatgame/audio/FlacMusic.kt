package com.serwylo.beatgame.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import org.jflac.FLACDecoder
import org.jflac.PCMProcessor
import org.jflac.metadata.StreamInfo
import org.jflac.util.ByteData
import java.io.InputStream
import kotlin.concurrent.thread

fun createMusic(file: FileHandle): Music {
    if (file.extension().lowercase() == "flac") {
        return FlacMusic(file)
    } else {
        return Gdx.audio.newMusic(file)
    }
}

class FlacMusic(private val fileHandle: FileHandle) : Music {
    private var isPlaying = false
    private var _isLooping = false
    private var _volume = 1.0f
    private var _pan = 0f
    private var _position = 0f

    private var audioDevice: AudioDevice? = null
    private var decoder: FLACDecoder? = null
    private var stream: InputStream? = null

    private var sampleRate = 44100
    private var channels = 2

    private var playbackThread: Thread? = null
    private var onCompletionListener: Music.OnCompletionListener? = null

    // A flag to signal the thread to stop
    @Volatile
    private var disposed = false

    init {
        initDecoder()
    }

    private fun initDecoder() {
        try {
            stream?.close()
        } catch (e: Exception) {}

        stream = fileHandle.read()
        decoder = FLACDecoder(stream)

        decoder?.addPCMProcessor(object : PCMProcessor {
            override fun processStreamInfo(info: StreamInfo) {
                sampleRate = info.sampleRate
                channels = info.channels
                if (audioDevice == null) {
                    audioDevice = Gdx.audio.newAudioDevice(sampleRate, channels == 1)
                }
            }

            override fun processPCM(pcm: ByteData) {
                if (!isPlaying || disposed) return

                val byteData = pcm.data
                val shortData = ShortArray(pcm.len / 2)
                for (i in shortData.indices) {
                    val low = byteData[i * 2].toInt() and 0xFF
                    val high = byteData[i * 2 + 1].toInt() shl 8
                    shortData[i] = (high or low).toShort()
                }

                audioDevice?.setVolume(_volume)
                audioDevice?.writeSamples(shortData, 0, shortData.size)

                _position += (shortData.size.toFloat() / channels.toFloat()) / sampleRate.toFloat()
            }
        })

        try {
            decoder?.readMetadata()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun play() {
        if (isPlaying) return
        isPlaying = true

        if (playbackThread == null || !playbackThread!!.isAlive) {
            playbackThread = thread(isDaemon = true) {
                try {
                    while (isPlaying && !disposed) {
                        try {
                            // JFlac decodeFrames loops internally until EOF
                            // Let's decode one frame at a time to be responsive to pause/stop
                            decoder?.readNextFrame()
                            decoder?.decodeFrames()

                            if (decoder?.isEOF == true) {
                                if (_isLooping) {
                                    initDecoder()
                                } else {
                                    isPlaying = false
                                    onCompletionListener?.onCompletion(this@FlacMusic)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isPlaying = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun pause() {
        isPlaying = false
    }

    override fun stop() {
        isPlaying = false
        initDecoder() // Reset to beginning
        _position = 0f
    }

    override fun isPlaying(): Boolean {
        return isPlaying
    }

    override fun setLooping(isLooping: Boolean) {
        this._isLooping = isLooping
    }

    override fun isLooping(): Boolean {
        return _isLooping
    }

    override fun setVolume(volume: Float) {
        this._volume = volume
        audioDevice?.setVolume(volume)
    }

    override fun getVolume(): Float {
        return _volume
    }

    override fun setPan(pan: Float, volume: Float) {
        this._pan = pan
        this._volume = volume
        audioDevice?.setVolume(volume)
    }

    override fun setPosition(position: Float) {
        val targetSample = (position * sampleRate).toLong()
        try {
            decoder?.seek(targetSample)
            this._position = position
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getPosition(): Float {
        return _position
    }

    override fun dispose() {
        disposed = true
        isPlaying = false
        try {
            playbackThread?.join(1000)
        } catch (e: Exception) {}

        audioDevice?.dispose()
        audioDevice = null
        try {
            stream?.close()
        } catch (e: Exception) {}
    }

    override fun setOnCompletionListener(listener: Music.OnCompletionListener?) {
        this.onCompletionListener = listener
    }
}
