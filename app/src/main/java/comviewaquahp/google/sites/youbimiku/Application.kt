package comviewaquahp.google.sites.youbimiku

class Application : android.app.Application() {

    companion object {
        lateinit var instance: Application private set
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
    }
}