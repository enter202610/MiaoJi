package com.inkledger.app.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI 兼容 /chat/completions 客户端（DeepSeek / 智谱GLM / 自定义均可） */
object LlmClient {

    data class Msg(val role: String, val content: String)

    /** 返回 (回复, null) 或 (null, 错误信息) */
    suspend fun chat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Msg>
    ): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.4)
                put("max_tokens", 700)
                put("stream", false)
                put(
                    "messages",
                    JSONArray(messages.map { JSONObject().put("role", it.role).put("content", it.content) })
                )
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }
            if (code !in 200..299) {
                return@withContext Pair(null, "请求失败($code)：${text?.take(160) ?: "网络错误"}")
            }
            val reply = JSONObject(text ?: return@withContext Pair(null, "空响应"))
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            Pair(reply.trim(), null)
        } catch (e: Exception) {
            Pair(null, "网络错误：${e.message ?: "请检查网络与配置"}")
        }
    }

    fun endpointFor(provider: String, customBase: String): String = when (provider) {
        "deepseek" -> "https://api.deepseek.com"
        "glm" -> "https://open.bigmodel.cn/api/paas/v4"
        else -> customBase.ifBlank { "https://open.bigmodel.cn/api/paas/v4" }
    }

    fun defaultModelFor(provider: String): String = when (provider) {
        "deepseek" -> "deepseek-chat"
        "glm" -> "glm-4-flash"
        else -> ""
    }
}
