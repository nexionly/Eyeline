package com.example.data.repository

import com.example.data.local.ScriptDao
import com.example.data.local.ScriptEntity
import kotlinx.coroutines.flow.Flow

class ScriptsRepository(
    private val scriptDao: ScriptDao,
) {
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.observeAllScripts()

    fun observeScript(id: Long): Flow<ScriptEntity?> = scriptDao.observeScriptById(id)

    suspend fun getScript(id: Long): ScriptEntity? = scriptDao.getScriptById(id)

    suspend fun getCount(): Int = scriptDao.getScriptCount()

    suspend fun createScript(title: String = "", content: String = ""): Long {
        val now = System.currentTimeMillis()
        return scriptDao.insertScript(
            ScriptEntity(
                title = title,
                content = content,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun saveScript(script: ScriptEntity): Long {
        val now = System.currentTimeMillis()
        return if (script.id == 0L) {
            scriptDao.insertScript(script.copy(createdAtMillis = now, updatedAtMillis = now))
        } else {
            scriptDao.updateScript(script.copy(updatedAtMillis = now))
            script.id
        }
    }

    suspend fun duplicateScript(source: ScriptEntity): Long {
        val now = System.currentTimeMillis()
        val copyTitle = if (source.title.isBlank()) "Untitled Copy" else "${source.title} (Copy)"
        return scriptDao.insertScript(
            ScriptEntity(
                title = copyTitle,
                content = source.content,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun deleteScript(id: Long) {
        scriptDao.deleteScriptById(id)
    }

    suspend fun seedStarterScriptIfNeeded(markSeeded: suspend () -> Unit) {
        if (scriptDao.getScriptCount() == 0) {
            val starterBody = """
                Here is the fastest way to look confident on camera without memorizing a single line.

                Keep your eyes locked right inside this glowing amber guide—it sits directly beneath your front camera lens.

                Tap anywhere on screen to pause the scroll if you want to improvise a thought, or drag the text up and down to jump right back to a hook.

                When you hit stop, watch your take immediately, reset in one tap, or save straight to your phone.
            """.trimIndent()
            createScript(
                title = "30-Second Hook Starter",
                content = starterBody,
            )
        }
        markSeeded()
    }
}
