package com.serwylo.beatgame.audio.fft

import com.serwylo.beatgame.audio.AudioData

data class FFTResultWithValues(
        val audioData: AudioData,
        val windowSize: Int,
        val windows: List<FFTWindowWithValues>
) {
    fun toResult() = FFTResult(audioData, windowSize, windows.map { it.toWindow() })
}