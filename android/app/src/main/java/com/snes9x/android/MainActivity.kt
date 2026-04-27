package com.snes9x.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snes9x.android.ui.theme.Snes9xTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EmulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.saveDir.mkdirs()
        setContent {
            Snes9xTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(viewModel: EmulatorViewModel) {
    val context   = LocalContext.current
    val recentRoms by viewModel.recentRoms.collectAsState()

    val pickRom = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val entry = viewModel.importRom(uri)
        if (entry != null) {
            context.startActivity(launchGameIntent(context, entry))
        } else {
            Toast.makeText(context, context.getString(R.string.rom_error), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickRom.launch(arrayOf("*/*")) },
                icon    = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                text    = { Text(stringResource(R.string.pick_rom)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (recentRoms.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text     = stringResource(R.string.recent_roms),
                    style    = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                RomList(
                    roms    = recentRoms,
                    onClick = { entry -> context.startActivity(launchGameIntent(context, entry)) }
                )
            }
        }
    }
}

private fun launchGameIntent(context: android.content.Context, entry: RomEntry) =
    Intent(context, GameActivity::class.java).apply {
        putExtra(GameActivity.EXTRA_ROM_PATH, entry.path)
        putExtra(GameActivity.EXTRA_ROM_NAME, entry.name)
    }

@Composable
private fun RomList(roms: List<RomEntry>, onClick: (RomEntry) -> Unit) {
    LazyColumn {
        items(roms.reversed()) { entry ->
            ListItem(
                headlineContent = {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingContent = {
                    Icon(Icons.Default.Gamepad, contentDescription = null)
                },
                modifier = Modifier.clickable { onClick(entry) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.Gamepad,
                contentDescription = null,
                modifier           = Modifier.size(64.dp),
                tint               = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text  = stringResource(R.string.no_roms),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
