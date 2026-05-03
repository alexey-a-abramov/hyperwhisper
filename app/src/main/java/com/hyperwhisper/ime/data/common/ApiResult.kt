package com.hyperwhisper.data

/**
 * Result wrapper for API calls
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T, val processingInfo: ProcessingInfo? = null) : ApiResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
