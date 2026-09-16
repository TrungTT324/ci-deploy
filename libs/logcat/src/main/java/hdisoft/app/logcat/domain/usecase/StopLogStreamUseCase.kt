package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.repository.LogcatRepository

class StopLogStreamUseCase(private val repository: LogcatRepository) {
    operator fun invoke() = repository.stopStream()
}
