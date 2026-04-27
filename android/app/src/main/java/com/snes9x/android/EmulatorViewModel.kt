package com.snes9x.android

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream

/**
 * ViewModel scoped to [MainActivity].
 *
 * Responsible for:
 *  - Copying ROM files from content URIs into app-private storage.
 *  - Tracking the recent-ROM list.
 *
 * The emulator lifecycle (init / load ROM / shutdown) is managed entirely
 * by [GameActivity], which is the only place the core actually runs.
 */
class EmulatorViewModel(app: Application) : AndroidViewModel(app) {

    private val _recentRoms = MutableStateFlow<List<RomEntry>>(emptyList())
    val recentRoms: StateFlow<List<RomEntry>> = _recentRoms

    val saveDir: File
        get() = File(getApplication<Application>().filesDir, "snes9x")

    /**
     * Copies the ROM at [uri] into app-private storage and returns a [RomEntry]
     * on success, or null on failure.
     */
    fun importRom(uri: Uri): RomEntry? {
        val context = getApplication<Application>()
        val doc = DocumentFile.fromSingleUri(context, uri) ?: return null
        val fileName = doc.name ?: return null

        val romsDir = File(saveDir, "roms").also { it.mkdirs() }
        val dest    = File(romsDir, fileName)

        // Copy only when the file has changed (size check)
        if (!dest.exists() || dest.length() != doc.length()) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } ?: return null
        }

        val stem  = fileName.substringBeforeLast('.')
        val entry = RomEntry(name = stem, path = dest.absolutePath)
        addRecent(entry)
        return entry
    }

    private fun addRecent(entry: RomEntry) {
        _recentRoms.value = (_recentRoms.value.filter { it.path != entry.path } + entry).takeLast(20)
    }
}

data class RomEntry(val name: String, val path: String)
