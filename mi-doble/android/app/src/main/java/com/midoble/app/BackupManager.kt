package com.midoble.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val PREFS = "mi_doble_profile"

    fun export(context: Context, destination: Uri, includeModels: Boolean): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val settings = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is String -> settings.put(key, JSONObject().put("type", "string").put("value", value))
                is Boolean -> settings.put(key, JSONObject().put("type", "boolean").put("value", value))
                is Int -> settings.put(key, JSONObject().put("type", "int").put("value", value))
                is Long -> settings.put(key, JSONObject().put("type", "long").put("value", value))
                is Float -> settings.put(key, JSONObject().put("type", "float").put("value", value.toDouble()))
                is Set<*> -> settings.put(key, JSONObject().put("type", "strings").put("value", JSONArray(value.filterIsInstance<String>())))
            }
        }
        context.contentResolver.openOutputStream(destination)?.use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                zip.putNextEntry(ZipEntry("mi-doble-backup/settings.json"))
                zip.write(settings.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (includeModels) {
                    val modelsDir = java.io.File(context.filesDir, "models")
                    if (modelsDir.exists()) modelsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(modelsDir).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry("mi-doble-backup/models/$relative"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("No se pudo crear el archivo de respaldo.")
        return if (includeModels) "Respaldo creado con configuración y modelos." else "Respaldo creado con configuración. Los modelos no se incluyeron."
    }

    fun restore(context: Context, source: Uri): String {
        var settings: JSONObject? = null
        val restoredModels = mutableListOf<java.io.File>()
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    when {
                        name == "mi-doble-backup/settings.json" -> settings = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        name.startsWith("mi-doble-backup/models/") && !entry.isDirectory -> {
                            val relative = name.removePrefix("mi-doble-backup/models/")
                            val segments = relative.split('/')
                            require(segments.isNotEmpty() && segments.none { it.isBlank() || it == "." || it == ".." }) { "Ruta de modelo no válida." }
                            val target = java.io.File(java.io.File(context.filesDir, "models"), relative)
                            require(target.canonicalPath.startsWith(java.io.File(context.filesDir, "models").canonicalPath + java.io.File.separator)) { "Ruta de modelo no válida." }
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            restoredModels += target
                        }
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("No se pudo abrir el respaldo.")
        val data = settings ?: error("El archivo no contiene una copia de la configuración de Mi Doble.")
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val item = data.getJSONObject(key)
            when (item.getString("type")) {
                "string" -> editor.putString(key, item.getString("value"))
                "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                "int" -> editor.putInt(key, item.getInt("value"))
                "long" -> editor.putLong(key, item.getLong("value"))
                "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                "strings" -> editor.putStringSet(key, item.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() })
            }
        }
        check(editor.commit()) { "No se pudieron restaurar los ajustes." }
        return "Respaldo restaurado. Modelos recuperados: ${restoredModels.size}. Reinicia Mi Doble para aplicar toda la configuración."
    }
}
