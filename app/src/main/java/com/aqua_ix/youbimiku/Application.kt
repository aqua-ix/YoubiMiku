package com.aqua_ix.youbimiku

class Application : android.app.Application() {

    companion object {
        lateinit var instance: Application private set
    }

    override fun onCreate() {
        super.onCreate()

        instance = this
    }
}