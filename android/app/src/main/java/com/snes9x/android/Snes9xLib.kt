package com.snes9x.android

import java.nio.ByteBuffer

/**
 * JNI bridge to the snes9x C++ core.
 *
 * Button indices match RETRO_DEVICE_ID_JOYPAD_* so they map directly to the
 * snes9x controls system set up in map_buttons() in snes9x_android.cpp.
 */
object Snes9xLib {

    init {
        System.loadLibrary("snes9x_android")
    }

    // SNES button indices
    const val BTN_B      = 0
    const val BTN_Y      = 1
    const val BTN_SELECT = 2
    const val BTN_START  = 3
    const val BTN_UP     = 4
    const val BTN_DOWN   = 5
    const val BTN_LEFT   = 6
    const val BTN_RIGHT  = 7
    const val BTN_A      = 8
    const val BTN_X      = 9
    const val BTN_L      = 10
    const val BTN_R      = 11

    /** Initialise the emulator; [saveDir] is an absolute path to app-private storage. */
    external fun nativeInit(saveDir: String): Boolean

    /** Load a ROM from [path]. Returns true on success. */
    external fun nativeLoadRom(path: String): Boolean

    /** Execute one SNES frame. Must be called from a single thread. */
    external fun nativeRunFrame()

    /**
     * Returns a [ByteBuffer] backed by the native frame buffer (RGB565, no copy).
     * Valid until the next [nativeRunFrame] call.
     */
    external fun nativeGetFrameBuffer(): ByteBuffer

    /** Width in pixels of the last rendered frame. */
    external fun nativeGetFrameWidth(): Int

    /** Height in pixels of the last rendered frame. */
    external fun nativeGetFrameHeight(): Int

    /**
     * Drains at most [maxSamples] stereo int16 samples into [buffer].
     * Returns the number of shorts actually written.
     */
    external fun nativeGetAudioSamples(buffer: ShortArray, maxSamples: Int): Int

    /** Report a button press or release. [button] is one of the BTN_* constants. */
    external fun nativeSetButton(button: Int, pressed: Boolean)

    /** Write a save-state to [path]. Returns true on success. */
    external fun nativeSaveState(path: String): Boolean

    /** Load a save-state from [path]. Returns true on success. */
    external fun nativeLoadState(path: String): Boolean

    /** Flush SRAM to disk and tear down the emulator. */
    external fun nativeShutdown()
}
