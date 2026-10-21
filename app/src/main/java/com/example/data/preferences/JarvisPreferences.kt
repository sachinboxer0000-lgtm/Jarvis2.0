package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisPreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_app_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
        const val KEY_SELECTED_MODEL = "selected_gemini_model"
        const val KEY_AVAILABLE_MODELS = "available_models_list"
        const val KEY_WAKE_PHRASE = "jarvis_wake_phrase"
        const val KEY_VOICE_LANG = "jarvis_voice_lang"
        const val KEY_TTS_ENABLED = "jarvis_tts_enabled"
        const val KEY_BACKGROUND_SERVICE = "jarvis_background_service"
        const val KEY_CONNECTION_STATUS = "jarvis_connection_status"
        const val KEY_CONNECTION_MESSAGE = "jarvis_connection_msg"

        val DEFAULT_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_WAKE_PHRASE = "Hey JARVIS"
        const val MEMORY_QUOTA_BYTES = 30L * 1024L * 1024L // 30 MiB
    }

    private val _apiKeyFlow = MutableStateFlow(getEffectiveApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private val _selectedModelFlow = MutableStateFlow(getSelectedModel())
    val selectedModelFlow: StateFlow<String> = _selectedModelFlow.asStateFlow()

    private val _modelsListFlow = MutableStateFlow(getAvailableModels())
    val modelsListFlow: StateFlow<List<String>> = _modelsListFlow.asStateFlow()

    private val _connectionStatusFlow = MutableStateFlow(getConnectionStatus())
    val connectionStatusFlow: StateFlow<ConnectionStatus> = _connectionStatusFlow.asStateFlow()

    private val _connectionMessageFlow = MutableStateFlow(getConnectionMessage())
    val connectionMessageFlow: StateFlow<String> = _connectionMessageFlow.asStateFlow()

    private val _wakePhraseFlow = MutableStateFlow(getWakePhrase())
    val wakePhraseFlow: StateFlow<String> = _wakePhraseFlow.asStateFlow()

    private val _voiceLangFlow = MutableStateFlow(getVoiceLanguage())
    val voiceLangFlow: StateFlow<String> = _voiceLangFlow.asStateFlow()

    private val _ttsEnabledFlow = MutableStateFlow(isTtsEnabled())
    val ttsEnabledFlow: StateFlow<Boolean> = _ttsEnabledFlow.asStateFlow()

    private val _backgroundServiceFlow = MutableStateFlow(isBackgroundServiceEnabled())
    val backgroundServiceFlow: StateFlow<Boolean> = _backgroundServiceFlow.asStateFlow()

    fun getCustomApiKey(): String = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""

    fun getEffectiveApiKey(): String {
        val customKey = getCustomApiKey().trim()
        if (customKey.isNotEmpty()) return customKey

        // Check if BuildConfig has GEMINI_API_KEY from Secrets panel / .env
        return try {
            val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
            val buildKey = field.get(null) as? String ?: ""
            if (buildKey.isNotEmpty() && !buildKey.contains("MY_GEMINI_API_KEY")) buildKey else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun isApiKeyConfigured(): Boolean = getEffectiveApiKey().isNotEmpty()

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key.trim()).apply()
        _apiKeyFlow.value = getEffectiveApiKey()
    }

    fun getSelectedModel(): String {
        return prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _selectedModelFlow.value = model
    }

    fun getAvailableModels(): List<String> {
        val raw = prefs.getString(KEY_AVAILABLE_MODELS, null)
        return if (raw.isNullOrBlank()) {
            DEFAULT_MODELS
        } else {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun setAvailableModels(models: List<String>) {
        val combined = models.joinToString(",")
        prefs.edit().putString(KEY_AVAILABLE_MODELS, combined).apply()
        _modelsListFlow.value = models
    }

    fun getWakePhrase(): String = prefs.getString(KEY_WAKE_PHRASE, DEFAULT_WAKE_PHRASE) ?: DEFAULT_WAKE_PHRASE

    fun setWakePhrase(phrase: String) {
        prefs.edit().putString(KEY_WAKE_PHRASE, phrase.trim()).apply()
        _wakePhraseFlow.value = phrase.trim()
    }

    fun getVoiceLanguage(): String = prefs.getString(KEY_VOICE_LANG, "hi-IN-hinglish") ?: "hi-IN-hinglish"

    fun setVoiceLanguage(lang: String) {
        prefs.edit().putString(KEY_VOICE_LANG, lang).apply()
        _voiceLangFlow.value = lang
    }

    fun isTtsEnabled(): Boolean = prefs.getBoolean(KEY_TTS_ENABLED, true)

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        _ttsEnabledFlow.value = enabled
    }

    fun isBackgroundServiceEnabled(): Boolean = prefs.getBoolean(KEY_BACKGROUND_SERVICE, false)

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SERVICE, enabled).apply()
        _backgroundServiceFlow.value = enabled
    }

    fun getConnectionStatus(): ConnectionStatus {
        val raw = prefs.getString(KEY_CONNECTION_STATUS, ConnectionStatus.UNCONFIGURED.name)
        return try {
            ConnectionStatus.valueOf(raw ?: ConnectionStatus.UNCONFIGURED.name)
        } catch (_: Exception) {
            ConnectionStatus.UNCONFIGURED
        }
    }

    fun getConnectionMessage(): String =
        prefs.getString(KEY_CONNECTION_MESSAGE, "Not tested yet. Please test connection.") ?: ""

    fun setConnectionResult(status: ConnectionStatus, message: String) {
        prefs.edit()
            .putString(KEY_CONNECTION_STATUS, status.name)
            .putString(KEY_CONNECTION_MESSAGE, message)
            .apply()
        _connectionStatusFlow.value = status
        _connectionMessageFlow.value = message
    }
}

enum class ConnectionStatus {
    UNCONFIGURED,
    TESTING,
    CONNECTED,
    ERROR
}
