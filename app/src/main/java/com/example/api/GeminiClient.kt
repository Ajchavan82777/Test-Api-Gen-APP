package com.example.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    
    // Use the task default model for basic text/prompt tasks as specified in the SKILL guidelines
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Enhances a simple prompt into a highly detailed, descriptive, and illustrative prompt
     * optimized for AI image generation models.
     */
    suspend fun enhancePrompt(userPrompt: String, customApiKey: String? = null): String = withContext(Dispatchers.IO) {
        // Retrieve key: prioritizing settings value, falling back to build-config
        val apiKey = if (!customApiKey.isNullOrBlank()) {
            customApiKey
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "No valid Gemini API key available.")
            return@withContext getLocalEnhancement(userPrompt)
        }

        val systemInstruction = "You are an expert prompt enhancer for AI image generators. " +
                "Your only task is to append descriptive, cinematic, and illustrative details to the user's original prompt. " +
                "You MUST begin your response with the exact words of the user's original prompt, followed by a comma, and then append the high-quality descriptors. " +
                "DO NOT delete, omit, rewrite, or paraphrase any words from the user's original prompt. " +
                "Only add rich visual descriptors around it, such as atmospheric lighting, intricate textures, environment details, artistic style, or professional rendering terms (e.g. volumetric lighting, ultra-realistic). " +
                "Keep the entire output extremely loyal, concise (under 80 words) and output ONLY the raw, appended prompt itself, without any introduction, labels, explanations, or quotes."

        // Build request body
        val requestJson = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Please enhance this prompt: $userPrompt")
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstruction)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 250)
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "Request failed code: ${response.code}, msg: $errorMsg")
                    return@withContext getLocalEnhancement(userPrompt)
                }

                val bodyString = response.body?.string() ?: return@withContext getLocalEnhancement(userPrompt)
                val responseJson = JSONObject(bodyString)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text")
                        if (!text.isNullOrBlank()) {
                            val rawEnhanced = text.trim()
                                .removeSurrounding("\"")
                                .removeSurrounding("\"")
                                .removeSurrounding("'")
                                .removeSurrounding("'")
                                .trim()
                            
                            // If the AI didn't include the original prompt or rewrote it, 
                            // we force the user's original exact prompt at the beginning of the generated string
                            return@withContext if (rawEnhanced.contains(userPrompt, ignoreCase = true)) {
                                rawEnhanced
                            } else {
                                "$userPrompt, $rawEnhanced"
                            }
                        }
                    }
                }
                return@withContext getLocalEnhancement(userPrompt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing request", e)
            return@withContext getLocalEnhancement(userPrompt)
        }
    }

    /**
     * Fallback rules to generate beautiful semantic local enhancements if user has no internet or no API key.
     */
    private fun getLocalEnhancement(prompt: String): String {
        val modifiers = listOf(
            "highly detailed, 8k resolution, photorealistic, cinematic volumetric lighting, ray tracing depths",
            "masterpiece art, intricate textures, unreal engine 5 aesthetic, golden ratio, dynamic ambient glow",
            "breathtaking visual depth, sharp digital realism, rich composition, professional concept art studio"
        ).random()
        return "$prompt, $modifiers"
    }
}
