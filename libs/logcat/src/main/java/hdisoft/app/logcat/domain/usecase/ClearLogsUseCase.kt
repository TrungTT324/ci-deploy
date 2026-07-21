package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.repository.LogcatRepository

class ClearLogsUseCase(private val repository: LogcatRepository) {
    suspend operator fun invoke(wasActive: Boolean): Boolean = repository.clearLogs(wasActive)
}
