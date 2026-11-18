package com.chesspattern.backend.api

import com.chesspattern.backend.domain.JobStatus
import com.chesspattern.backend.service.ImportException
import com.chesspattern.backend.service.JobService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Тело запроса импорта. Поля — `var` с умолчаниями: так Jackson собирает объект
 * без Kotlin-модуля (в M1 он не подключён).
 */
class ImportRequest {
    var platform: String? = null
    var username: String? = null
    var limit: Int? = null
    var perfType: String? = null
}

data class JobAcceptedResponse(
    val jobId: UUID,
    val status: JobStatus,
)

@RestController
@RequestMapping("/api/v1")
class ImportController(private val jobService: JobService) {

    /** Ставит импорт в очередь и сразу отвечает: разбор 20 партий идёт минутами. */
    @PostMapping("/import")
    fun import(@RequestBody request: ImportRequest): ResponseEntity<JobAcceptedResponse> {
        val platform = (request.platform ?: "lichess").lowercase()
        if (platform != "lichess") {
            throw ImportException("Платформа «$platform» пока не поддерживается: только lichess")
        }
        val job = jobService.enqueue(
            player = request.username.orEmpty(),
            limit = request.limit,
            perfType = request.perfType,
        )
        return ResponseEntity.accepted().body(JobAcceptedResponse(jobId = job.id, status = job.status))
    }
}
