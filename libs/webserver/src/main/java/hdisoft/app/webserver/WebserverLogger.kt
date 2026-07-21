package hdisoft.app.webserver

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WebserverLogger {
    private val _logs = MutableLiveData<List<String>>(emptyList())
    val logs: LiveData<List<String>> = _logs

    private val logList = mutableListOf<String>()

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logLine = "[$timestamp] $message"
        
        // Run on main thread to update LiveData safely
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            logList.add(0, logLine) // Insert at top (most recent first)
            if (logList.size > 200) {
                logList.removeAt(logList.size - 1)
            }
            _logs.value = logList.toList()
        }
    }

    fun clear() {
        logList.clear()
        _logs.value = emptyList()
    }
}
