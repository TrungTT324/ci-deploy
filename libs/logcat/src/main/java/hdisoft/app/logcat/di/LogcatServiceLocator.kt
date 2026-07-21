package hdisoft.app.logcat.di

import android.content.Context
import hdisoft.app.logcat.data.datasource.LocalLogcatDataSource
import hdisoft.app.logcat.data.datasource.WebSocketDataSource
import hdisoft.app.logcat.data.repository.LogcatRepositoryImpl
import hdisoft.app.logcat.domain.repository.LogcatRepository
import hdisoft.app.logcat.domain.usecase.*

object LogcatServiceLocator {

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() = appContext ?: throw IllegalStateException("LogcatServiceLocator has not been initialized. Call init(context) first.")

    private val webSocketDataSource by lazy { WebSocketDataSource() }
    private val localLogcatDataSource by lazy { LocalLogcatDataSource() }

    var externalLogStreamer: hdisoft.app.logcat.domain.repository.ExternalLogStreamer? = null

    val logcatRepository: LogcatRepository by lazy {
        LogcatRepositoryImpl(context, webSocketDataSource, localLogcatDataSource)
    }

    val getStreamConfigUseCase by lazy { GetStreamConfigUseCase(logcatRepository) }
    val saveStreamConfigUseCase by lazy { SaveStreamConfigUseCase(logcatRepository) }
    val startLogStreamUseCase by lazy { StartLogStreamUseCase(logcatRepository) }
    val stopLogStreamUseCase by lazy { StopLogStreamUseCase(logcatRepository) }
    val isStreamingActiveUseCase by lazy { IsStreamingActiveUseCase(logcatRepository) }
    val getStreamStatusTextUseCase by lazy { GetStreamStatusTextUseCase(logcatRepository) }
    val clearLogsUseCase by lazy { ClearLogsUseCase(logcatRepository) }
    val exportLogsUseCase by lazy { ExportLogsUseCase(logcatRepository) }
}
