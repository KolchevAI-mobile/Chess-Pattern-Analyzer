package com.chesspattern.backend.api

import com.chesspattern.backend.service.ImportException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientException

data class ApiError(val error: String)

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ImportException::class)
    fun handleImport(ex: ImportException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(ApiError(ex.message ?: "Ошибка импорта"))

    @ExceptionHandler(RestClientException::class)
    fun handleUpstream(ex: RestClientException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError("Внешний сервис недоступен: ${ex.message}"))
}
