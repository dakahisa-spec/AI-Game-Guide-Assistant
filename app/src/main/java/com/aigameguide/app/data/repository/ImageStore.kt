package com.aigameguide.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImageStore(private val context: Context) {
    suspend fun importUris(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        uris.take(5).mapNotNull { uri ->
            runCatching {
                val file = File(dir, "shot_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            }.getOrNull()
        }
    }

    fun newCameraTarget(): Pair<Uri, String> {
        val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file.absolutePath
    }
}
