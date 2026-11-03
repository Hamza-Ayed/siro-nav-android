package com.siro.nav.voice

import android.content.Context
import android.media.MediaPlayer
import com.siro.nav.engine.NavEvent
import org.json.JSONObject

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

    fun hasPrompt(id: String): Boolean = prompts.containsKey(id)
    fun getPrompt(id: String): VoicePrompt? = prompts[id]
}

class NavVoiceEngine(private val context: Context) {
    var isMuted: Boolean = false
    private var mediaPlayer: MediaPlayer? = null
    private var lastPlayedPromptId: String? = null

    init {
        NavVoiceCatalog.instance.loadManifest(context)
    }

    fun resolvePromptId(
        maneuverType: Int,
        distanceMeters: Double,
        priority: Int = 2,
        exitNumber: Int? = null
    ): String? {
        // Immediate turn (<= 35m)
        if (distanceMeters <= 35.0 || priority == 0) {
            return resolveImmediatePrompt(maneuverType, exitNumber)
        }

        // Quantized distance bins matching studio audio library
        if (distanceMeters in 35.0..65.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_50m_turn_right"
                -1, -2, -3 -> "after_50m_turn_left"
                6, -6 -> "after_50m_roundabout"
                else -> null
            }
        }

        if (distanceMeters in 65.0..130.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_100m_turn_right"
                -1, -2, -3 -> "after_100m_turn_left"
                8, -8, -98 -> "after_100m_u_turn"
                else -> null
            }
        }

        if (distanceMeters in 130.0..180.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_150m_turn_right"
                -1, -2, -3 -> "after_150m_turn_left"
                else -> null
            }
        }

        if (distanceMeters in 180.0..235.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_200m_turn_right"
                -1, -2, -3 -> "after_200m_turn_left"
                else -> null
            }
        }

        if (distanceMeters in 235.0..350.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_300m_turn_right"
                -1, -2, -3 -> "after_300m_turn_left"
                6, -6 -> "after_300m_roundabout"
                else -> null
            }
        }

        if (distanceMeters in 350.0..650.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_500m_turn_right"
                -1, -2, -3 -> "after_500m_turn_left"
                6, -6 -> "after_500m_roundabout"
                4, 5 -> "after_500m_exit"
                else -> null
            }
        }

        if (distanceMeters in 650.0..1200.0) {
            return when (maneuverType) {
                1, 2, 3 -> "after_1km_turn_right"
                -1, -2, -3 -> "after_1km_turn_left"
                4, 5 -> "after_1km_exit"
                else -> null
            }
        }

        return null
    }

    private fun resolveImmediatePrompt(maneuverType: Int, exitNumber: Int?): String {
        return when (maneuverType) {
            1, 2 -> "turn_right_now"
            -1, -2 -> "turn_left_now"
            3 -> "turn_right_now"
            -3 -> "turn_left_now"
            8, -8, -98 -> "u_turn_now"
            4, 5 -> "take_exit_now"
            6, -6 -> if (exitNumber != null && exitNumber in 1..6) "roundabout_exit_$exitNumber" else "enter_roundabout_now"
            else -> "straight_through_intersection"
        }
    }

    fun handleInstruction(instruction: NavEvent.Instruction) {
        val promptId = resolvePromptId(
            maneuverType = instruction.maneuverType,
            distanceMeters = instruction.distanceMeters,
            priority = instruction.priority,
            exitNumber = instruction.exitNumber
        )
        if (promptId != null && promptId != lastPlayedPromptId) {
            lastPlayedPromptId = promptId
            playPrompt(promptId)
        }
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
