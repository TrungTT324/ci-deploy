package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.repository.LogcatRepository

class StartLogStreamUseCase(private val repository: LogcatRepository) {
    operator fun invoke(onLogReceived: (String) -> Unit, onStatusChanged: (String) -> Unit) {
        repository.startStream(onLogReceived, onStatusChanged)
    }
}
