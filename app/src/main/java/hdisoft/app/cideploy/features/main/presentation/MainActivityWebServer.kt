package hdisoft.app.cideploy.features.main.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import hdisoft.app.cideploy.R
import hdisoft.app.core.utils.NetworkUtils
import hdisoft.app.webserver.HttpWebServerService
import hdisoft.app.webserver.WebserverSettingsActivity

/**
 * :libs:webserver (local web console) integration for [MainActivity], split
 * out here since it's a self-contained concern. Kotlin extension functions
 * can't add new instance state, so the bound-service fields
 * (`webServerService`/`isWebServerBound`/`webServerConnection`) stay
 * `internal` properties on MainActivity itself — this file only adds
 * behavior over them.
 */

internal fun MainActivity.startAndBindWebServer() {
    val webServerIntent = Intent(this, HttpWebServerService::class.java)
    startService(webServerIntent)
    bindService(webServerIntent, webServerConnection, Context.BIND_AUTO_CREATE)
}

internal fun MainActivity.unbindWebServerIfBound() {
    if (isWebServerBound) {
        unbindService(webServerConnection)
        isWebServerBound = false
    }
}

internal fun MainActivity.openWebserverSettings() {
    startActivity(Intent(this, WebserverSettingsActivity::class.java))
}

internal fun MainActivity.updateWebConsoleInfo() {
    val service = webServerService
    val tvWebConsoleInfo: TextView = findViewById(R.id.tvWebConsoleInfo)
    val switchWebserver: androidx.appcompat.widget.SwitchCompat = findViewById(R.id.switchWebserver)
    
    val isRunning = HttpWebServerService.isRunning
    switchWebserver.isChecked = isRunning

    if (isRunning && service != null) {
        val localIp = NetworkUtils.getLocalIpAddress(this) ?: "127.0.0.1"
        val port = service.getPort()
        val pin = service.getPin()
        val requireAuth = service.isRequireAuthen()

        tvWebConsoleInfo.text = if (requireAuth) {
            "Console: http://$localIp:$port | PIN: $pin"
        } else {
            "Console: http://$localIp:$port | Auth: OFF"
        }
        tvWebConsoleInfo.setTextColor(android.graphics.Color.parseColor("#81C784"))
    } else {
        tvWebConsoleInfo.text = "Console: Server is OFFLINE"
        tvWebConsoleInfo.setTextColor(android.graphics.Color.parseColor("#E57373"))
    }
}

/**
 * Builds the "Web Console Server" section of the Settings dialog and appends
 * it to [layout]. Returns a callback the caller must invoke on Save.
 */
internal fun MainActivity.addWebConsoleSettingsSection(layout: LinearLayout, dp: Float): () -> Unit {
    val context = this
    val service = webServerService

    val tvWebHeader = TextView(context).apply {
        val port = service?.getPort() ?: HttpWebServerService.defaultPort
        text = "Web Console Server (Port $port)"
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.BLACK)
    }
    layout.addView(tvWebHeader)

    val cbRequireAuth = CheckBox(context).apply {
        text = "Require PIN Authentication"
        isChecked = service?.isRequireAuthen() ?: false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
    }
    layout.addView(cbRequireAuth)

    val pinLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
    }

    val tvPin = TextView(context).apply {
        text = "Security PIN: ${service?.getPin() ?: "1234"}"
        textSize = 15f
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    val btnGenPin = Button(context).apply {
        text = "Generate"
        setOnClickListener {
            val newPin = service?.generateNewPin() ?: "1234"
            tvPin.text = "Security PIN: $newPin"
            updateWebConsoleInfo()
            Toast.makeText(context, "New PIN generated: $newPin", Toast.LENGTH_SHORT).show()
        }
    }

    pinLayout.addView(tvPin)
    pinLayout.addView(btnGenPin)
    layout.addView(pinLayout)

    return {
        service?.let {
            it.setRequireAuthen(cbRequireAuth.isChecked)
            updateWebConsoleInfo()
        }
    }
}
