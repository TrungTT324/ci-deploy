package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.repository.LogcatRepository
import java.io.File

class ExportLogsUseCase(private val repository: LogcatRepository) {
    suspend operator fun invoke(logs: List<String>, cacheDir: File): File? = repository.exportLogs(logs, cacheDir)
}
