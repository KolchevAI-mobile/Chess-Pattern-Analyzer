package com.chesspattern.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chesspattern.data.ApiClient
import com.chesspattern.data.ApiException
import com.chesspattern.data.GameSummary
import com.chesspattern.data.Insights
import com.chesspattern.data.Pattern

private sealed interface UiState {
    data object Idle : UiState
    data class Loading(val processed: Int, val total: Int, val status: String) : UiState
    data class Ready(val insights: Insights, val games: List<GameSummary>) : UiState
    data class Error(val message: String) : UiState
}

private const val GAMES_LIMIT = 10
private const val POLL_INTERVAL_MS = 1500L
private const val MAX_POLLS = 240

/**
 * MVP-экран: ник Lichess → постановка импорта в очередь → прогресс → диагноз.
 * Всё в одном экране: проверяем ценность до появления навигации.
 */
@Composable
fun ChessPatternApp(modifier: Modifier = Modifier) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<UiState>(UiState.Idle) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun analyze() {
        val player = nickname.trim()
        if (player.isEmpty()) return
        state = UiState.Loading(0, GAMES_LIMIT, "QUEUED")
        Thread {
            val result = runCatching {
                val api = ApiClient()
                val jobId = api.startImport(player, limit = GAMES_LIMIT)
                var polls = 0
                while (polls++ < MAX_POLLS) {
                    val job = api.job(jobId)
                    handler.post { state = UiState.Loading(job.processed, job.total, job.status) }
                    when (job.status) {
                        "DONE" -> break
                        "FAILED" -> throw ApiException(job.error ?: "Импорт не удался")
                    }
                    Thread.sleep(POLL_INTERVAL_MS)
                }
                val insights = api.insights(player)
                val games = api.games(player, limit = GAMES_LIMIT)
                insights to games
            }
            handler.post {
                result.fold(
                    onSuccess = { (insights, games) -> state = UiState.Ready(insights, games) },
                    onFailure = { state = UiState.Error(it.message ?: "Не удалось получить анализ") },
                )
            }
        }.start()
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Chess Pattern Analyzer", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Найдём, какие ошибки повторяются у тебя от партии к партии",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Ник на Lichess") },
                singleLine = true,
                enabled = state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = ::analyze,
                enabled = nickname.isNotBlank() && state !is UiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Проанализировать $GAMES_LIMIT партий")
            }

            when (val current = state) {
                is UiState.Idle -> Text(
                    "Партии берутся из публичного профиля Lichess. Нужен режим rapid.",
                    style = MaterialTheme.typography.bodySmall,
                )

                is UiState.Loading -> LoadingCard(current)

                is UiState.Error -> Text(
                    current.message,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Ready -> {
                    Diagnosis(current.insights)
                    Games(current.games)
                    TextButton(onClick = { state = UiState.Idle }) {
                        Text("Другой ник")
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(loading: UiState.Loading) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(
                when (loading.status) {
                    "QUEUED" -> "Задание в очереди…"
                    "RUNNING" -> "Разбираем партии…"
                    else -> loading.status
                },
            )
        }
        if (loading.processed == 0) {
            // Stockfish считает весь набор одним batch-вызовом: до его ответа
            // честнее показать неопределённый прогресс, чем «0 из 10».
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "Анализируем партии в Stockfish — это самая долгая часть",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LinearProgressIndicator(
                progress = { loading.processed.toFloat() / loading.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Обработано ${loading.processed} из ${loading.total}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Diagnosis(insights: Insights) {
    Text(
        "Диагноз · ${insights.gamesAnalysed} партий",
        style = MaterialTheme.typography.titleMedium,
    )

    val top = insights.patterns.firstOrNull()
    if (top == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Явного паттерна не нашлось", fontWeight = FontWeight.SemiBold)
                Text(
                    "Ошибки либо редки, либо данных пока мало — сыграйте ещё несколько партий.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(top.title, style = MaterialTheme.typography.titleLarge)
            Text(top.summary, style = MaterialTheme.typography.bodyMedium)
            Text(
                "уверенность ${(top.confidence * 100).toInt()}% · сила ${(top.severity * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
            if (top.confidence < 0.3) {
                Text(
                    "Данных пока мало — вывод осторожный.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    insights.patterns.drop(1).forEach { pattern ->
        PatternRow(pattern)
    }

    insights.breakingPoint?.let { breaking ->
        Text(
            "Надлом: в ${breaking.gamesWithBreakingPoint} партиях" +
                (breaking.dominantPhase?.let { ", чаще в фазе «$it»" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PatternRow(pattern: Pattern) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(pattern.title, fontWeight = FontWeight.SemiBold)
            Text(pattern.summary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Games(games: List<GameSummary>) {
    Spacer(Modifier.height(4.dp))
    Text("Партии", style = MaterialTheme.typography.titleMedium)
    games.forEach { game ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("${game.white} — ${game.black}", fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append(game.result)
                        game.accuracy?.let { append(" · точность $it%") }
                        game.blunders?.let { append(" · зевков $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
