package com.serwylo.beatgame.audio.flac

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

/**
 * A custom Music implementation that plays FLAC files using jflac and LibGDX's AudioDevice.
 * This avoids needing a temporary PCM/WAV file.
 */
class FlacMusic(val file: FileHandle) : Music {
    private var isPlaying = false
    private var isLooping = false
    private var volume = 1f
    private var position = 0f
    private var onCompletionListener: Music.OnCompletionListener? = null

    private var audioDevice: AudioDevice? = null
    private var playThread: Thread? = null

    private var sampleRate = 44100
    private var channels = 2

    override fun play() {
        if (isPlaying) return
        isPlaying = true

        playThread = thread {
            var stream: InputStream? = null
            try {
                stream = file.read()
                val decoder = FLACDecoder(stream)

                var deviceInitialized = false

                decoder.addPCMProcessor(object : PCMProcessor {
                    override fun processStreamInfo(streamInfo: StreamInfo) {
                        sampleRate = streamInfo.sampleRate
                        channels = streamInfo.channels
                        if (!deviceInitialized) {
                            audioDevice = Gdx.audio.newAudioDevice(sampleRate, channels == 1)
                            audioDevice?.setVolume(volume)
                            deviceInitialized = true
                        }
                    }

                    override fun processPCM(pcm: ByteData) {
                        if (!isPlaying) {
                            throw InterruptedException("Stopped")
                        }

                        // Convert byte data to short array for LibGDX AudioDevice
                        val bytes = pcm.data
                        val len = pcm.len
                        val shorts = ShortArray(len / 2)
                        var i = 0
                        var j = 0
                        while (i < len) {
                            // little endian conversion
                            val b1 = bytes[i].toInt() and 0xFF
                            val b2 = bytes[i + 1].toInt() shl 8
                            shorts[j] = (b1 or b2).toShort()
                            i += 2
                            j++
                        }

                        audioDevice?.writeSamples(shorts, 0, shorts.size)
                        position += shorts.size.toFloat() / channels / sampleRate
                    }
                })

                decoder.decode()

                if (isPlaying) {
                    isPlaying = false
                    if (isLooping) {
                        position = 0f
                        play()
                    } else {
                        Gdx.app.postRunnable {
                            onCompletionListener?.onCompletion(this@FlacMusic)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // Expected when stopping
            } catch (e: Exception) {
                Gdx.app.error("FlacMusic", "Error playing FLAC", e)
            } finally {
                stream?.close()
                audioDevice?.dispose()
                audioDevice = null
            }
        }
    }

    override fun pause() {
        isPlaying = false
        playThread?.join()
        playThread = null
    }

    override fun stop() {
        isPlaying = false
        position = 0f
        playThread?.join()
        playThread = null
    }

    override fun isPlaying(): Boolean = isPlaying
    override fun setLooping(isLooping: Boolean) { this.isLooping = isLooping }
    override fun isLooping(): Boolean = isLooping
    override fun setVolume(volume: Float) {
        this.volume = volume
        audioDevice?.setVolume(volume)
    }
    override fun getVolume(): Float = volume
    override fun setPan(pan: Float, volume: Float) {
        // AudioDevice doesn't support panning out of the box, just set volume
        setVolume(volume)
    }
    override fun setPosition(position: Float) {
        // Seeking in FLAC with JFLAC is complex and stream needs to be recreated.
        // For our simple purpose, we'll reset.
        this.position = position
        if (isPlaying) {
            stop()
            play()
        }
    }
    override fun getPosition(): Float = position
    override fun dispose() {
        stop()
    }
    override fun setOnCompletionListener(listener: Music.OnCompletionListener?) {
        this.onCompletionListener = listener
    }
}