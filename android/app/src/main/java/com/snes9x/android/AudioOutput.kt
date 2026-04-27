package com.snes9x.android

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Wraps an [AudioTrack] in streaming mode.
 *
 * Designed to be fed from the emulation thread: call [write] once per frame
 * with the samples produced by [Snes9xLib.nativeGetAudioSamples].
 */
class AudioOutput {

    private val sampleRate   = 32040
    private val channelMask  = AudioFormat.CHANNEL_OUT_STEREO
    private val encoding     = AudioFormat.ENCODING_PCM_16BIT

    // Per-frame sample count (stereo shorts) with a comfortable headroom
    private val bufferFrames = 4096

    private val audioTrack: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .setEncoding(encoding)
                .build()
        )
        .setBufferSizeInBytes(bufferFrames * 2 * 2) // stereo * 2 bytes/sample
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    fun start() {
        audioTrack.play()
    }

    /**
     * Write [count] shorts from [samples] to the AudioTrack.
     * Uses WRITE_NON_BLOCKING so the emulation thread is never stalled.
     */
    fun write(samples: ShortArray, count: Int) {
        if (count <= 0) return
        audioTrack.write(samples, 0, count, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun pause() {
        audioTrack.pause()
    }

    fun release() {
        audioTrack.stop()
        audioTrack.release()
    }
}
