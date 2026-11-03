package com.siro.nav.voice

import android.content.Context
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class VoicePrompt(
    val file: String,
    val text: String,
    val category: String,
    val priority: Int = 2
)

class NavVoiceCatalog private constructor() {
    private val prompts = mutableMapOf<String, VoicePrompt>()

    companion object {
        val instance: NavVoiceCatalog by lazy { NavVoiceCatalog() }
    }

    fun loadManifest(context: Context) {
        try {
            val jsonString = context.assets.open("audio/ar/manifest.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val promptsObj = root.optJSONObject("prompts") ?: return
            val keys = promptsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = promptsObj.getJSONObject(key)
                prompts[key] = VoicePrompt(
                    file = item.getString("file"),
                    text = item.getString("text"),
                    category = item.getString("category"),
                    priority = item.optInt("priority", 2)
                )
            }
        } catch (_: Exception) {}
    }

    fun registerPrompt(id: String, prompt: VoicePrompt) {
        prompts[id] = prompt
    }

    fun getPrompt(id: String): VoicePrompt? = prompts[id]
    val count: Int get() = prompts.size
}

class NavVoiceEngine(private val context: Context) {
    var isMuted: Boolean = false
    private var mediaPlayer: MediaPlayer? = null

    init {
        NavVoiceCatalog.instance.loadManifest(context)
    }

    fun playPrompt(id: String) {
        if (isMuted) return
        val prompt = NavVoiceCatalog.instance.getPrompt(id) ?: return
        try {
            mediaPlayer?.release()
            val afd = context.assets.openFd("audio/ar/${prompt.file}")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }
}
