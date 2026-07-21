package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.model.LogStreamConfig
import hdisoft.app.logcat.domain.repository.LogcatRepository

class GetStreamConfigUseCase(private val repository: LogcatRepository) {
    operator fun invoke(): LogStreamConfig = repository.getStreamConfig()
}
