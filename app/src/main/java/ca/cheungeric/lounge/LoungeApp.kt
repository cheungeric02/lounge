package ca.cheungeric.lounge

import android.app.Application

class LoungeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Lives for the whole process: package-change receiver + initial app query.
        container.appsRepository.start()
    }
}
