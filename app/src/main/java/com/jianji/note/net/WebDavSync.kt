package com.jianji.note.net

import com.jianji.note.data.Prefs
import com.jianji.note.data.Repository
import com.jianji.note.util.Backup

/**
 * 把 WebDAV 客户端、备份序列化和仓库串起来。
 * 采用「整份文件覆盖」的快照式同步：上传会把云端文件整体替换成本机当前内容，
 * 恢复会用云端文件整体替换本机数据。不做双向合并。
 */
object WebDavSync {

    const val DEFAULT_FILE_NAME = "jianji-backup.json"

    fun fileName(): String = Prefs.webdavFileName.ifBlank { DEFAULT_FILE_NAME }

    fun clientOrNull(): WebDavClient? {
        if (!Prefs.webdavConfigured) return null
        return WebDavClient(Prefs.webdavUrl, Prefs.webdavUser, Prefs.webdavPassword)
    }

    suspend fun upload(repo: Repository): WebDavResult<Unit> {
        val client = clientOrNull() ?: return WebDavResult.Error("还没有配置 WebDAV")
        val json = Backup.encode(repo.noteDao.loadAll(), repo.folderDao.loadAll())
        return when (val result = client.upload(fileName(), json)) {
            is WebDavResult.Success -> {
                rememberSync("上传成功")
                result
            }
            is WebDavResult.Error -> {
                Prefs.webdavLastResult = result.message
                result
            }
        }
    }

    suspend fun restore(repo: Repository, remoteName: String): WebDavResult<Int> {
        val client = clientOrNull() ?: return WebDavResult.Error("还没有配置 WebDAV")
        return when (val downloaded = client.download(remoteName)) {
            is WebDavResult.Error -> downloaded
            is WebDavResult.Success -> {
                val parsed = try {
                    Backup.decode(downloaded.data)
                } catch (e: Exception) {
                    return WebDavResult.Error("云端文件不是有效的备份：${e.message.orEmpty()}")
                }
                val count = repo.replaceAll(parsed.folders, parsed.notes)
                rememberSync("恢复成功（$count 条）")
                WebDavResult.Success(count)
            }
        }
    }

    suspend fun listBackups(): WebDavResult<List<WebDavParser.RemoteEntry>> {
        val client = clientOrNull() ?: return WebDavResult.Error("还没有配置 WebDAV")
        return when (val listed = client.listFiles()) {
            is WebDavResult.Error -> listed
            is WebDavResult.Success ->
                WebDavResult.Success(listed.data.filter { !it.isDirectory })
        }
    }

    private fun rememberSync(message: String) {
        Prefs.webdavLastSync = System.currentTimeMillis()
        Prefs.webdavLastResult = message
    }
}
