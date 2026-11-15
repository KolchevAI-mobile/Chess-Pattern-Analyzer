package com.chesspattern.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP-клиент backend-а.
 *
 * Сознательно на `HttpURLConnection` + `org.json`: для M1 не тянем Retrofit и Hilt,
 * чтобы не раздувать сборку до первого экрана. Когда появится DI и экранов станет больше,
 * клиент меняется на Retrofit без изменения API слоя данных.
 */
class ApiClient(private val baseUrl: String = BASE_URL) {

    fun importGames(player: String, limit: Int = 10, perfType: String = "rapid") {
        val body = JSONObject()
            .put("platform", "lichess")
            .put("username", player)
            .put("limit", limit)
            .put("perfType", perfType)
            .toString()
        request("/api/v1/import", method = "POST", body = body)
    }

    fun games(player: String, limit: Int = 10): List<GameSummary> {
        val json = request("/api/v1/games?player=${encode(player)}&limit=$limit")
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            GameSummary(
                id = item.getString("id"),
                white = item.optString("white"),
                black = item.optString("black"),
                result = item.optString("result"),
                accuracy = item.optDoubleOrNull("accuracy"),
                blunders = item.optIntOrNull("blunders"),
                mistakes = item.optIntOrNull("mistakes"),
                inaccuracies = item.optIntOrNull("inaccuracies"),
                analysed = item.optBoolean("analysed", false),
            )
        }
    }

    fun insights(player: String): Insights {
        val json = request("/api/v1/insights?player=${encode(player)}")
        val root = JSONObject(json)
        val patternsJson = root.optJSONArray("patterns") ?: JSONArray()
        val patterns = (0 until patternsJson.length()).map { index ->
            val item = patternsJson.getJSONObject(index)
            val evidenceJson = item.optJSONArray("evidence") ?: JSONArray()
            Pattern(
                code = item.optString("code"),
                title = item.optString("title"),
                summary = item.optString("summary"),
                severity = item.optDouble("severity", 0.0),
                confidence = item.optDouble("confidence", 0.0),
                evidence = (0 until evidenceJson.length()).map { evidenceIndex ->
                    val evidence = evidenceJson.getJSONObject(evidenceIndex)
                    Evidence(
                        gameId = evidence.optString("gameId"),
                        ply = evidence.optIntOrNull("ply"),
                        san = evidence.optString("san").takeIf { it.isNotBlank() },
                        detail = evidence.optString("detail").takeIf { it.isNotBlank() },
                    )
                },
            )
        }

        val breaking = root.optJSONObject("breakingPoint")?.let {
            BreakingSummary(
                gamesWithBreakingPoint = it.optInt("gamesWithBreakingPoint", 0),
                medianPly = it.optDoubleOrNull("medianPly"),
                dominantPhase = it.optString("dominantPhase").takeIf { phase -> phase.isNotBlank() },
            )
        }

        return Insights(
            player = root.optString("player", player),
            gamesAnalysed = root.optInt("gamesAnalysed", 0),
            patterns = patterns.sortedByDescending { it.severity * it.confidence },
            breakingPoint = breaking,
        )
    }

    private fun request(path: String, method: String = "GET", body: String? = null): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            // Разбор 10–20 партий на слабом сервере идёт минутами.
            readTimeout = 120_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw ApiException(readError(text, code))
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun readError(text: String, code: Int): String =
        runCatching { JSONObject(text).optString("error") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Сервис ответил $code"

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    companion object {
        /** 10.0.2.2 — localhost машины из Android-эмулятора. */
        const val BASE_URL = "http://10.0.2.2:8080"
    }
}
