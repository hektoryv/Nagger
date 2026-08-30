package com.example.nag.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backups are plain JSON. Pick a folder once and every change afterwards rewrites
 * nag-backup.json in it, so an uninstall or a broken update can't take the history with it.
 */
object Backup {

    private const val KEY_FOLDER = "backup_folder"
    private const val FILE_NAME = "nag-backup.json"
    private const val MIME = "application/json"

    fun toJson(context: Context): String {
        val habits = JSONArray().apply {
            Store.loadHabits(context).forEach { put(Json.habitToJson(it)) }
        }
        val log = JSONArray().apply {
            Store.loadLog(context).forEach { put(Json.entryToJson(it)) }
        }
        val (hour, minute) = Store.reminderTime(context)
        return JSONObject().apply {
            put("version", 3)
            put("habits", habits)
            put("log", log)
            put("hour", hour)
            put("minute", minute)
        }.toString(2)
    }

    fun writeTo(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(toJson(context).toByteArray())
        }
        true
    }.getOrDefault(false)

    fun restoreFrom(context: Context, uri: Uri): Boolean = runCatching {
        val text = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() } ?: return false
        val root = JSONObject(text)

        val habits = Json.habitsFromString(root.getJSONArray("habits").toString())
        val log = Json.logFromString((root.optJSONArray("log") ?: JSONArray()).toString())
        if (habits.isEmpty() && log.isEmpty()) return false

        Store.replaceAll(context, habits, log, root.optInt("hour", 23), root.optInt("minute", 0))
        true
    }.getOrDefault(false)

    // ---------- automatic ----------

    fun folder(context: Context): Uri? =
        Store.prefs(context).getString(KEY_FOLDER, null)?.let { Uri.parse(it) }

    fun folderLabel(context: Context): String? =
        folder(context)?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment
        }

    /** Remembers the folder and takes a permission that survives reboots. */
    fun setFolder(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        Store.prefs(context).edit().putString(KEY_FOLDER, uri.toString()).apply()
        auto(context)
    }.getOrDefault(false)

    fun clearFolder(context: Context) {
        Store.prefs(context).edit().remove(KEY_FOLDER).apply()
    }

    /**
     * Rewrites the backup file if a folder has been chosen. Silent by design: it runs after
     * every change and shouldn't interrupt anything if the folder has gone away.
     */
    fun auto(context: Context): Boolean = runCatching {
        val tree = folder(context) ?: return false
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return false
        if (!dir.canWrite()) return false
        val existing = dir.findFile(FILE_NAME)
        val target = existing ?: dir.createFile(MIME, FILE_NAME) ?: return false
        writeTo(context, target.uri)
    }.getOrDefault(false)
}
