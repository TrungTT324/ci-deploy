package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.repository.LogcatRepository

class IsStreamingActiveUseCase(private val repository: LogcatRepository) {
    operator fun invoke(): Boolean = repository.isStreamingActive()
}
