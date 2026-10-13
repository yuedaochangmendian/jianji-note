package com.jianji.note

import android.app.Application
import com.jianji.note.data.AppDatabase
import com.jianji.note.data.Prefs
import com.jianji.note.data.Repository
import com.jianji.note.net.WebDavSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        scheduleAutoBackup()
    }

    /** 开了「启动时自动备份」且有配置的话，冷启动后静默上传一次快照。 */
    private fun scheduleAutoBackup() {
        if (!Prefs.webdavAutoBackup || !Prefs.webdavConfigured) return
        appScope.launch { WebDavSync.upload(repo) }
    }

    companion object {
        lateinit var instance: App
            private set

        val repo: Repository get() = instance.repo

        val appScope: CoroutineScope get() = instance.appScope
    }
}
