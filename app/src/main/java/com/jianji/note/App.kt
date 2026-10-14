package com.jianji.note

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
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

    /** 前台 activity 计数，只有全部退到后台才重新上锁。 */
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        Prefs.applyNightMode()
        Prefs.unlocked = false
        registerActivityLifecycleCallbacks(LifecycleWatcher())
        scheduleAutoBackup()
    }

    /**
     * 判断「应用是否退到后台」用前台 activity 计数，而不是在某个页面的 onStop 里解锁。
     * 之前 MainActivity.onStop 直接上锁，而打开编辑页时 Main 会被覆盖、同样触发 onStop，
     * 结果每打开一条笔记再返回就要重输一次密码。
     * 旋屏和主题切换（isChangingConfigurations）不算退后台。
     */
    private inner class LifecycleWatcher : ActivityLifecycleCallbacks {

        override fun onActivityStarted(activity: Activity) {
            if (activity.isChangingConfigurations) return
            startedActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity.isChangingConfigurations) return
            startedActivities--
            if (startedActivities <= 0) {
                startedActivities = 0
                if (Prefs.passcodeEnabled) Prefs.unlocked = false
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
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
