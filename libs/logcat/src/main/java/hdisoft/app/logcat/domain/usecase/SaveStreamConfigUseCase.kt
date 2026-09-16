package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.model.LogStreamConfig
import hdisoft.app.logcat.domain.repository.LogcatRepository

class SaveStreamConfigUseCase(private val repository: LogcatRepository) {
    operator fun invoke(config: LogStreamConfig) = repository.saveStreamConfig(config)
}
