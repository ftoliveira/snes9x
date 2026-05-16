package com.snes9x.android

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.snes9x.android.ui.theme.Snes9xTheme

/**
 * Simple dialog shown when the user presses the menu/options button inside
 * the game. Provides save state, load state, reset and quit actions.
 */
class GameMenuDialog(
    context: Context,
    private val onSave:   (Int)   -> Boolean,
    private val onLoad:   (Int)   -> Boolean,
    private val slotExists: (Int) -> Boolean,
    private val onReset:  ()      -> Unit,
    private val onQuit:   ()      -> Unit,
) : Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ComposeView(context).apply {
            setContent {
                Snes9xTheme {
                    Surface(shape = MaterialTheme.shapes.large) {
                        GameMenuContent(
                            onSave      = { slot -> dismiss(); onSave(slot) },
                            onLoad      = { slot -> dismiss(); onLoad(slot) },
                            slotExists  = slotExists,
                            onReset     = { dismiss(); onReset() },
                            onQuit      = { dismiss(); onQuit() },
                            onDismiss   = { dismiss() }
                        )
                    }
                }
            }
        }
        setContentView(view)
    }
}

@Composable
private fun GameMenuContent(
    onSave:     (Int) -> Unit,
    onLoad:     (Int) -> Unit,
    slotExists: (Int) -> Boolean,
    onReset:    ()    -> Unit,
    onQuit:     ()    -> Unit,
    onDismiss:  ()    -> Unit,
) {
    val slots = 0..4

    Column(
        modifier = Modifier
            .widthIn(min = 240.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.menu),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))

        Text(stringResource(R.string.save_state), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            slots.forEach { slot ->
                OutlinedButton(
                    onClick      = { onSave(slot) },
                    modifier     = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("${slot + 1}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(stringResource(R.string.load_state), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            slots.forEach { slot ->
                OutlinedButton(
                    onClick      = { onLoad(slot) },
                    enabled      = slotExists(slot),
                    modifier     = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Text("${slot + 1}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reset))
            }
            Button(onClick = onQuit, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.quit))
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.resume))
        }
    }
}
