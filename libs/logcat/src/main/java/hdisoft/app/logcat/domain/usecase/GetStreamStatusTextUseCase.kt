package hdisoft.app.logcat.domain.usecase

import hdisoft.app.logcat.domain.repository.LogcatRepository

class GetStreamStatusTextUseCase(private val repository: LogcatRepository) {
    operator fun invoke(): String = repository.getStreamStatusText()
}
