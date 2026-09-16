package hdisoft.app.logcat.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hdisoft.app.logcat.di.LogcatServiceLocator
import hdisoft.app.logcat.domain.model.LogStreamConfig
import kotlinx.coroutines.launch
import java.io.File

class LogcatViewModel : ViewModel() {

    private val getStreamConfigUseCase = LogcatServiceLocator.getStreamConfigUseCase
    private val saveStreamConfigUseCase = LogcatServiceLocator.saveStreamConfigUseCase
    private val startLogStreamUseCase = LogcatServiceLocator.startLogStreamUseCase
    private val stopLogStreamUseCase = LogcatServiceLocator.stopLogStreamUseCase
    private val isStreamingActiveUseCase = LogcatServiceLocator.isStreamingActiveUseCase
    private val getStreamStatusTextUseCase = LogcatServiceLocator.getStreamStatusTextUseCase
    private val clearLogsUseCase = LogcatServiceLocator.clearLogsUseCase
    private val exportLogsUseCase = LogcatServiceLocator.exportLogsUseCase

    private val _streamConfig = MutableLiveData<LogStreamConfig>()
    val streamConfig: LiveData<LogStreamConfig> = _streamConfig

    private val _isStreaming = MutableLiveData<Boolean>()
    val isStreaming: LiveData<Boolean> = _isStreaming

    private val _statusText = MutableLiveData<String>()
    val statusText: LiveData<String> = _statusText

    private val _logLine = MutableLiveData<String>()
    val logLine: LiveData<String> = _logLine

    private val _exportFile = MutableLiveData<File?>()
    val exportFile: LiveData<File?> = _exportFile

    private val _clearLogsDone = MutableLiveData<Boolean>()
    val clearLogsDone: LiveData<Boolean> = _clearLogsDone

    fun loadConfig() {
        _streamConfig.value = getStreamConfigUseCase()
        _isStreaming.value = isStreamingActiveUseCase()
        _statusText.value = getStreamStatusTextUseCase()
    }

    fun saveConfig(mode: LogStreamConfig.Mode, ip: String, port: Int, protocol: LogStreamConfig.Protocol) {
        val newConfig = LogStreamConfig(mode, ip, port, protocol)
        saveStreamConfigUseCase(newConfig)
        _streamConfig.value = newConfig
    }

    fun toggleStream() {
        val active = isStreamingActiveUseCase()
        if (active) {
            stopLogStreamUseCase()
            _isStreaming.value = false
            _statusText.value = getStreamStatusTextUseCase()
        } else {
            startStream()
        }
    }

    fun startStream() {
        startLogStreamUseCase(
            onLogReceived = { line ->
                _logLine.postValue(line)
            },
            onStatusChanged = { status ->
                _statusText.postValue(status)
                _isStreaming.postValue(isStreamingActiveUseCase())
            }
        )
        _isStreaming.value = true
    }

    fun stopStream() {
        stopLogStreamUseCase()
        _isStreaming.value = false
        _statusText.value = getStreamStatusTextUseCase()
    }

    fun clearLogs() {
        val wasActive = isStreamingActiveUseCase()
        viewModelScope.launch {
            clearLogsUseCase(wasActive)
            _clearLogsDone.value = true
            if (wasActive) {
                startStream()
            }
        }
    }

    fun resetClearLogsFlag() {
        _clearLogsDone.value = false
    }

    fun exportLogs(logs: List<String>, cacheDir: File) {
        viewModelScope.launch {
            val file = exportLogsUseCase(logs, cacheDir)
            _exportFile.value = file
        }
    }

    fun postLogLine(line: String) {
        _logLine.postValue(line)
    }

    fun postStatus(status: String) {
        _statusText.postValue(status)
        _isStreaming.postValue(isStreamingActiveUseCase())
    }

    fun resetExportFileFlag() {
        _exportFile.value = null
    }
}
