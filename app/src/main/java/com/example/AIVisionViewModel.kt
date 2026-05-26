package com.example

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.HistoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.net.URLEncoder
import com.example.data.AvatarEntity
import kotlinx.coroutines.Dispatchers

sealed interface Screen {
    object Home : Screen
    object Settings : Screen
    object Result : Screen
    object History : Screen
    object Profile : Screen
}

enum class GenerationMode {
    TEXT_TO_IMAGE, IMAGE_TO_IMAGE
}

data class ImagePreset(val name: String, val url: String, val author: String)

class AIVisionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AIVisionViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = HistoryRepository(database.historyDao(), database.avatarDao())

    // UI Navigation
    val currentScreen = MutableStateFlow<Screen>(Screen.Home)
    
    // AI Avatar Identity Lock States
    val selectedAvatar = MutableStateFlow<AvatarEntity?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allAvatars: StateFlow<List<AvatarEntity>> = repository.allAvatars
        .catch { e ->
            Log.e(TAG, "Fatal error flow mapping: allAvatars database initialization failure", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User Generation State Inputs
    val mode = MutableStateFlow(GenerationMode.TEXT_TO_IMAGE)
    val promptInput = MutableStateFlow("")
    val negativePromptInput = MutableStateFlow("")
    val selectedAspectRatio = MutableStateFlow("1:1") // "1:1", "4:5", "9:16", "16:9", "3:4"
    val selectedResolution = MutableStateFlow("1024x1024") // "512x512", "768x1024", "1024x1024", "1080x1920", "1920x1080"
    val selectedStyle = MutableStateFlow("Realistic") // "Realistic", "Cinematic", "Anime", "3D", "Digital Art", "Logo", "Poster"
    val selectedQuality = MutableStateFlow("HD") // "Standard", "HD", "Ultra HD"
    val selectedCount = MutableStateFlow(1) // 1, 2, 4
    val seedInput = MutableStateFlow("")
    val advancedExpanded = MutableStateFlow(false)
    val enablePromptEnhancement = MutableStateFlow(true)

    // Preset Image Reference for Image-to-Image Mode
    val presets = listOf(
        ImagePreset("Cyber Samurai", "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&auto=format&fit=crop", "Anime Studio"),
        ImagePreset("Nebula Gateway", "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?w=600&auto=format&fit=crop", "NASA"),
        ImagePreset("Neon Cyberpunk City", "https://images.unsplash.com/photo-1515621061946-eff1c2a352bd?w=600&auto=format&fit=crop", "Cyber Art"),
        ImagePreset("Mystic Forest Grid", "https://images.unsplash.com/photo-1511497584788-876760111969?w=600&auto=format&fit=crop", "Nature Tech")
    )
    val selectedPresetRef = MutableStateFlow(presets[0])

    // Key settings and metrics
    val customApiKey = MutableStateFlow("") // Custom override key in settings block
    val selectedModel = MutableStateFlow("gemini-3.5-flash (Standard)")
    val usageCounter = MutableStateFlow(0)

    // Image Generation Output state
    val isGenerating = MutableStateFlow(false)
    val generationProgressText = MutableStateFlow("")
    val generationProgressValue = MutableStateFlow(0f)
    val lastGeneratedImage = MutableStateFlow<HistoryEntity?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

    // History and Gallery search flow
    val historySearchQuery = MutableStateFlow("")
    val historyFilterStyle = MutableStateFlow("All")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val allGenerations: StateFlow<List<HistoryEntity>> = historySearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allGenerations
            } else {
                repository.search(query)
            }
        }.combine(historyFilterStyle) { list, style ->
            if (style == "All") {
                list
            } else {
                list.filter { it.style.lowercase() == style.lowercase() }
            }
        }
        .catch { e ->
            Log.e(TAG, "Fatal error flow mapping: allGenerations history retrieval error", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Initialize setup key or preferences if needed
        val prefs = application.getSharedPreferences("ai_vision_settings", Application.MODE_PRIVATE)
        customApiKey.value = prefs.getString("gemini_api_key", "") ?: ""
        usageCounter.value = prefs.getInt("generation_count", 0)
        enablePromptEnhancement.value = prefs.getBoolean("enable_prompt_enhancement", true)

        // Seed default high fidelity AI Avatars for instant trial
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hasSeeded = prefs.getBoolean("avatars_seeded_v1", false)
                if (!hasSeeded) {
                    val dao = database.avatarDao()
                    dao.insertAvatar(
                        AvatarEntity(
                            name = "Aria Nova (Cosmic Agent)",
                            description = "Intrepid space exploration lead. High-contrast cyber-visor, futuristic silver braids, and sleek armored thermal gear.",
                            faceImagesJson = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop," +
                                             "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400&auto=format&fit=crop," +
                                             "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&auto=format&fit=crop",
                            characterSheet = "https://images.unsplash.com/photo-1541462608141-2f52c6f1d4b6?w=600&auto=format&fit=crop",
                            lockStrength = "Strict"
                        )
                    )
                    dao.insertAvatar(
                        AvatarEntity(
                            name = "Vance Kaelen (Exo Fighter)",
                            description = "Daring interstellar orbital pilot. Striking copper-toned asymmetric haircut, defined frame, and lightweight pressurized flight fatigues.",
                            faceImagesJson = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&auto=format&fit=crop," +
                                             "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&auto=format&fit=crop," +
                                             "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=400&auto=format&fit=crop",
                            characterSheet = "https://images.unsplash.com/photo-1539650116574-8efeb43e2750?w=600&auto=format&fit=crop",
                            lockStrength = "High"
                        )
                    )
                    prefs.edit().putBoolean("avatars_seeded_v1", true).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Database seed incident", e)
            }
        }
    }

    fun createAvatar(name: String, description: String, faceImages: List<String>, characterSheet: String, lockStrength: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val faceImagesJson = faceImages.filter { it.isNotBlank() }.joinToString(",")
            val avatar = AvatarEntity(
                name = name,
                description = description,
                faceImagesJson = faceImagesJson,
                characterSheet = characterSheet,
                lockStrength = lockStrength
            )
            repository.insertAvatar(avatar)
        }
    }

    fun deleteAvatar(avatarId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAvatar(avatarId)
            if (selectedAvatar.value?.id == avatarId) {
                selectedAvatar.value = null
            }
        }
    }

    fun selectAvatar(avatar: AvatarEntity?) {
        selectedAvatar.value = avatar
    }

    fun saveApiKey(key: String) {
        customApiKey.value = key
        val prefs = getApplication<Application>().getSharedPreferences("ai_vision_settings", Application.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun setPromptEnhancement(enabled: Boolean) {
        enablePromptEnhancement.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences("ai_vision_settings", Application.MODE_PRIVATE)
        prefs.edit().putBoolean("enable_prompt_enhancement", enabled).apply()
    }

    private fun incrementUsage() {
        usageCounter.value += 1
        val prefs = getApplication<Application>().getSharedPreferences("ai_vision_settings", Application.MODE_PRIVATE)
        prefs.edit().putInt("generation_count", usageCounter.value).apply()
    }

    fun navigateTo(screen: Screen) {
        currentScreen.value = screen
    }

    fun generateImage() {
        val prompt = promptInput.value.trim()
        if (prompt.isEmpty()) {
            errorMessage.value = "Please enter an image description prompt."
            return
        }

        viewModelScope.launch {
            try {
                isGenerating.value = true
                errorMessage.value = null
                
                // Progress simulation phase 1
                generationProgressText.value = "Analyzing description context..."
                generationProgressValue.value = 0.15f
                delay(1200)

                // Progress simulation phase 2: Live Gemini API Enhancement!
                val enhanced = if (enablePromptEnhancement.value) {
                    generationProgressText.value = "AI Engine: Enhancing visual detail semantics..."
                    generationProgressValue.value = 0.35f
                    try {
                        GeminiClient.enhancePrompt(prompt, customApiKey.value)
                    } catch (e: Exception) {
                        val fallback = "$prompt, detailed volumetric rendering, Unreal Engine 5 look"
                        Log.e(TAG, "Gemini enhancement error", e)
                        fallback
                    }
                } else {
                    generationProgressText.value = "Processing visual blueprint..."
                    generationProgressValue.value = 0.35f
                    prompt
                }

                // Inject Identity Lock prompts as requested
                val activeAvatar = selectedAvatar.value
                val enhancedWithIdentity = if (activeAvatar != null) {
                    "$enhanced. Use the selected avatar face reference images and character sheet as the only identity source. Preserve the avatar’s exact face identity, hairstyle, hair color, skin tone, body structure, age appearance, and natural proportions. Do not reinterpret, beautify, replace, or alter the identity. Generate the new image using the same avatar identity in the requested pose, outfit, background, and style."
                } else {
                    enhanced
                }

                generationProgressValue.value = 0.55f
                delay(1000)

                // Progress simulation phase 3
                generationProgressText.value = "Synthesizing latent diffusion network..."
                generationProgressValue.value = 0.75f
                delay(1400)

                generationProgressText.value = "Polishing cybernetic composition render..."
                generationProgressValue.value = 0.90f
                delay(1000)

                // Inject Negative Rules based on lock strength
                val addedNegativePrompt = if (activeAvatar != null) {
                    val baseNeg = when (activeAvatar.lockStrength) {
                        "Strict" -> "face change, beautifying avatar, altering hairstyle, changing skin tone, changing body shape, creating new person, mixing identity, face swap, older look, younger look"
                        "High" -> "face change, altering hairstyle, changing skin tone, changing body shape, making look older or younger"
                        else -> "face change, body shape change"
                    }
                    if (negativePromptInput.value.isNotBlank()) {
                        "${negativePromptInput.value}, $baseNeg"
                    } else {
                        baseNeg
                    }
                } else {
                    negativePromptInput.value
                }

                // Assemble pollinations engine image generation query
                val baseSeed = seedInput.value.ifBlank { (100000..999999).random().toString() }
                val encodedPrompt = URLEncoder.encode(
                    "${selectedStyle.value} style, $enhancedWithIdentity" + 
                    if (addedNegativePrompt.isNotBlank()) " (avoid: $addedNegativePrompt)" else "", 
                    "UTF-8"
                )
                
                // Adjust pollinations resolution bounds
                val resTokens = selectedResolution.value.split("x")
                val width = resTokens.getOrNull(0)?.toIntOrNull() ?: 1024
                val height = resTokens.getOrNull(1)?.toIntOrNull() ?: 1024

                // Add seed to guarantee deterministic generations
                val finalImageUrl = "https://image.pollinations.ai/p/$encodedPrompt?width=$width&height=$height&seed=$baseSeed&nologo=true"

                val newGeneration = HistoryEntity(
                    prompt = prompt,
                    enhancedPrompt = enhanced,
                    negativePrompt = negativePromptInput.value,
                    imageUrl = finalImageUrl,
                    style = selectedStyle.value,
                    aspectRatio = selectedAspectRatio.value,
                    resolution = selectedResolution.value,
                    quality = selectedQuality.value,
                    numImages = selectedCount.value,
                    seed = baseSeed,
                    timestamp = System.currentTimeMillis()
                )

                // Persist into Room Database for Gallery history
                val insertedId = repository.insert(newGeneration)
                val finalGeneration = newGeneration.copy(id = insertedId)
                
                lastGeneratedImage.value = finalGeneration
                incrementUsage()

                generationProgressValue.value = 1.0f
                generationProgressText.value = "Generative render complete!"
                delay(500)

                isGenerating.value = false
                navigateTo(Screen.Result)

            } catch (e: Exception) {
                isGenerating.value = false
                errorMessage.value = "Generative network collision: ${e.localizedMessage ?: "Unknown error"}"
                Log.e(TAG, "Image generation error", e)
            }
        }
    }

    fun reopenResult(generation: HistoryEntity) {
        lastGeneratedImage.value = generation
        navigateTo(Screen.Result)
    }

    fun deleteGeneration(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            if (lastGeneratedImage.value?.id == id) {
                lastGeneratedImage.value = null
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            lastGeneratedImage.value = null
        }
    }
}
