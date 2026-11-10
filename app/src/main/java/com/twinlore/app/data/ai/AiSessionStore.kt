package com.twinlore.app.data.ai

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists one chat session per vault as JSON under filesDir/ai_assistant.
 * Not a database (deliberate): a small JSON file the user can wipe via "Clear session".
 */
@Singleton
class AiSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()
    private val root: File get() = File(context.filesDir, "ai_assistant").apply { mkdirs() }
    private val sessionsDir: File get() = File(root, "sessions").apply { mkdirs() }

    private fun sessionFile(vaultId: String): File = File(sessionsDir, "${sanitize(vaultId)}.json")

    /** Directory where this vault's attachment PNGs live. */
    fun imageDir(vaultId: String): File = File(File(root, "images"), sanitize(vaultId))

    fun load(vaultId: String): AiSession {
        val f = sessionFile(vaultId)
        if (!f.exists()) return AiSession(vaultId)
        return try {
            gson.fromJson(f.readText(), AiSession::class.java) ?: AiSession(vaultId)
        } catch (_: Exception) {
            AiSession(vaultId)
        }
    }

    fun save(session: AiSession) {
        try {
            sessionFile(session.vaultId).writeText(gson.toJson(session))
        } catch (_: Exception) {
            // Best-effort persistence; a failed write must not crash the chat.
        }
    }

    fun clear(vaultId: String) {
        sessionFile(vaultId).delete()
        imageDir(vaultId).deleteRecursively()
    }

    /** Copies [src] into this vault's image dir, returns the absolute destination path. */
    fun copyImageIntoSession(vaultId: String, src: File, timestamp: Long): String {
        val dir = imageDir(vaultId).apply { mkdirs() }
        val dest = File(dir, "$timestamp.png")
        src.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
