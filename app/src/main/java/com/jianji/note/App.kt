package com.jianji.note

import android.app.Application
import com.jianji.note.data.AppDatabase
import com.jianji.note.data.Prefs
import com.jianji.note.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    private val database by lazy { AppDatabase.get(this) }

    val repo: Repository by lazy { Repository(database) }

    /** 不随界面生命周期取消的作用域，用于「退出时保存」这类必须完成的写入。 */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        Prefs.applyNightMode()
        Prefs.unlocked = false
    }

    companion object {
        lateinit var instance: App
            private set

        val repo: Repository get() = instance.repo

        val appScope: CoroutineScope get() = instance.appScope
    }
}
