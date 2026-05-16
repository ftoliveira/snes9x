package com.snes9x.android

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File

/**
 * Full-screen game activity.
 *
 * Extras:
 *  - [EXTRA_ROM_PATH] – absolute path to the ROM file (already in app-private storage).
 *  - [EXTRA_ROM_NAME] – display name used to build save-state file names.
 */
class GameActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_ROM_NAME = "rom_name"
    }

    private val romPath by lazy { intent.getStringExtra(EXTRA_ROM_PATH) ?: "" }
    private val romName by lazy { intent.getStringExtra(EXTRA_ROM_NAME) ?: "rom" }
    private val saveDir by lazy { File(filesDir, "snes9x") }
    private val statesDir by lazy { File(saveDir, "states").also { it.mkdirs() } }

    private lateinit var surfaceView: GameSurfaceView
    private lateinit var audioOutput: AudioOutput

    // Axis state for delta-based button events
    private var lastHatX = 0f;  private var lastHatY = 0f
    private var lastAxisX = 0f; private var lastAxisY = 0f

    // Android keycode → SNES button index mapping
    private val keyMap = mapOf(
        KeyEvent.KEYCODE_BUTTON_A      to Snes9xLib.BTN_B,
        KeyEvent.KEYCODE_BUTTON_B      to Snes9xLib.BTN_A,
        KeyEvent.KEYCODE_BUTTON_X      to Snes9xLib.BTN_Y,
        KeyEvent.KEYCODE_BUTTON_Y      to Snes9xLib.BTN_X,
        KeyEvent.KEYCODE_BUTTON_L1     to Snes9xLib.BTN_L,
        KeyEvent.KEYCODE_BUTTON_R1     to Snes9xLib.BTN_R,
        KeyEvent.KEYCODE_BUTTON_L2     to Snes9xLib.BTN_L,
        KeyEvent.KEYCODE_BUTTON_R2     to Snes9xLib.BTN_R,
        KeyEvent.KEYCODE_BUTTON_SELECT to Snes9xLib.BTN_SELECT,
        KeyEvent.KEYCODE_BUTTON_START  to Snes9xLib.BTN_START,
        KeyEvent.KEYCODE_BUTTON_THUMBL to Snes9xLib.BTN_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBR to Snes9xLib.BTN_START,
        KeyEvent.KEYCODE_DPAD_UP       to Snes9xLib.BTN_UP,
        KeyEvent.KEYCODE_DPAD_DOWN     to Snes9xLib.BTN_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT     to Snes9xLib.BTN_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT    to Snes9xLib.BTN_RIGHT,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (romPath.isEmpty()) { finish(); return }

        hideSystemUi()

        // Init core (idempotent) then load ROM
        Snes9xLib.nativeInit(saveDir.absolutePath)
        Snes9xLib.nativeLoadRom(romPath)

        audioOutput = AudioOutput()
        surfaceView = GameSurfaceView(this)
        surfaceView.setAudioOutput(audioOutput)
        setContentView(surfaceView)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        surfaceView.onGameResume()
    }

    override fun onPause() {
        super.onPause()
        surfaceView.onGamePause()
    }

    // ── Save / load state helpers ─────────────────────────────────────────────

    private fun stateSlotPath(slot: Int) =
        File(statesDir, "$romName.$slot.sst").absolutePath

    private fun slotExists(slot: Int) = File(stateSlotPath(slot)).exists()

    private fun saveState(slot: Int): Boolean {
        val ok = Snes9xLib.nativeSaveState(stateSlotPath(slot))
        Toast.makeText(this, if (ok) R.string.save_ok else R.string.state_error, Toast.LENGTH_SHORT).show()
        return ok
    }

    private fun loadState(slot: Int): Boolean {
        if (!slotExists(slot)) return false
        val ok = Snes9xLib.nativeLoadState(stateSlotPath(slot))
        Toast.makeText(this, if (ok) R.string.load_ok else R.string.state_error, Toast.LENGTH_SHORT).show()
        return ok
    }

    // ── In-game menu (menu button or controller MODE button) ──────────────────

    private fun showGameMenu() {
        surfaceView.onGamePause()
        GameMenuDialog(
            context     = this,
            onSave      = ::saveState,
            onLoad      = ::loadState,
            slotExists  = ::slotExists,
            onReset     = {
                Snes9xLib.nativeInit(saveDir.absolutePath)
                Snes9xLib.nativeLoadRom(romPath)
            },
            onQuit      = ::finish
        ).apply {
            setOnDismissListener { surfaceView.onGameResume() }
            show()
        }
    }

    // ── Gamepad key events ────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE
        ) {
            if (event.action == KeyEvent.ACTION_UP) showGameMenu()
            return true
        }
        val snesBtn = keyMap[event.keyCode] ?: return super.dispatchKeyEvent(event)
        Snes9xLib.nativeSetButton(snesBtn, event.action == KeyEvent.ACTION_DOWN)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and android.view.InputDevice.SOURCE_JOYSTICK == 0)
            return super.onGenericMotionEvent(event)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        updateAxis(hatX, lastHatX, Snes9xLib.BTN_LEFT, Snes9xLib.BTN_RIGHT)
        updateAxis(hatY, lastHatY, Snes9xLib.BTN_UP,   Snes9xLib.BTN_DOWN)
        lastHatX = hatX; lastHatY = hatY

        val axisX = event.getAxisValue(MotionEvent.AXIS_X)
        val axisY = event.getAxisValue(MotionEvent.AXIS_Y)
        updateAxis(axisX, lastAxisX, Snes9xLib.BTN_LEFT, Snes9xLib.BTN_RIGHT)
        updateAxis(axisY, lastAxisY, Snes9xLib.BTN_UP,   Snes9xLib.BTN_DOWN)
        lastAxisX = axisX; lastAxisY = axisY

        return true
    }

    private fun updateAxis(cur: Float, prev: Float, negBtn: Int, posBtn: Int, t: Float = 0.5f) {
        if ((cur < -t) != (prev < -t)) Snes9xLib.nativeSetButton(negBtn, cur < -t)
        if ((cur >  t) != (prev >  t)) Snes9xLib.nativeSetButton(posBtn, cur >  t)
    }

    // ── Immersive mode ────────────────────────────────────────────────────────

    private fun hideSystemUi() {
        window.insetsController?.apply {
            hide(WindowInsets.Type.systemBars() or WindowInsets.Type.navigationBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.decorView.keepScreenOn = true
    }
}
