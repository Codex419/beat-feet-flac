package com.serwylo.beatgame.audio.fft

import com.serwylo.beatgame.audio.AudioData

data class FFTResult(
        val audioData: AudioData,
        val windowSize: Int,
        val windows: List<FFTWindow>
)
