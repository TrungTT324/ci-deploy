package hdisoft.app.cideploy

import android.app.Application
import hdisoft.app.cideploy.di.ServiceLocator
import hdisoft.app.cideploy.features.main.data.datasource.SocketStreamerImpl
import hdisoft.app.logcat.di.LogcatServiceLocator

class CiDeployApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        LogcatServiceLocator.externalLogStreamer = SocketStreamerImpl()
    }
}
