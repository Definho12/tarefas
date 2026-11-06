package com.guilherme.tarefas

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Guarda as tarefas num arquivo JSON dentro do próprio app. */
object TaskStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "tarefas.json")

    @Synchronized
    fun all(ctx: Context): List<Task> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { Task.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            // Guarda uma cópia do arquivo com problema em vez de perdê-lo
            runCatching { f.copyTo(File(ctx.filesDir, "tarefas-com-erro.json"), overwrite = true) }
            emptyList()
        }
    }

    @Synchronized
    private fun write(ctx: Context, list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(ctx.filesDir, "tarefas.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file(ctx))) {
            file(ctx).writeText(arr.toString())
            tmp.delete()
        }
    }

    fun get(ctx: Context, id: Int): Task? = all(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(ctx: Context, t: Task): Task {
        val list = all(ctx).toMutableList()
        val saved = if (t.id == 0) t.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) else t
        val i = list.indexOfFirst { it.id == saved.id }
        if (i >= 0) list[i] = saved else list += saved
        write(ctx, list)
        return saved
    }

    @Synchronized
    fun delete(ctx: Context, id: Int) {
        write(ctx, all(ctx).filter { it.id != id })
    }
}
