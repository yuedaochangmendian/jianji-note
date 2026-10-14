package com.jianji.note.net

import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 极简 WebDAV 客户端：只用得上 PROPFIND / PUT / GET / MKCOL 四个方法，
 * 所以直接用 OkHttp 手写，不引入第三方 WebDAV 库。
 */
class WebDavClient(
    baseUrl: String,
    user: String,
    password: String,
) {

    /** 目录地址，统一保证以 / 结尾，方便直接拼文件名。 */
    val baseUrl: String = normalize(baseUrl)

    private val authorization: String = Credentials.basic(user, password, Charsets.UTF_8)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun fileUrl(fileName: String): String = baseUrl + fileName

    suspend fun testConnection(): WebDavResult<Unit> =
        execute(propfindRequest("0")) { Unit }

    suspend fun listFiles(): WebDavResult<List<WebDavParser.RemoteEntry>> =
        execute(propfindRequest("1")) { body -> WebDavParser.parseMultiStatus(body) }

    suspend fun upload(fileName: String, content: String): WebDavResult<Unit> {
        val request = Request.Builder()
            .url(fileUrl(fileName))
            .put(content.toRequestBody(JSON_MEDIA))
            .header("Authorization", authorization)
            .build()

        val result = execute(request) { Unit }
        // 409 一般是目标目录还不存在，试着创建一次再重试
        if (result is WebDavResult.Error && result.httpCode == 409) {
            return when (val created = makeDirectory()) {
                is WebDavResult.Success -> execute(request) { Unit }
                is WebDavResult.Error -> created
            }
        }
        return result
    }

    suspend fun download(fileName: String): WebDavResult<String> {
        val request = Request.Builder()
            .url(fileUrl(fileName))
            .get()
            .header("Authorization", authorization)
            .build()
        return execute(request) { body -> body }
    }

    /** 目录不存在时创建它（MKCOL）。父目录也不存在时会返回 409。 */
    suspend fun makeDirectory(): WebDavResult<Unit> {
        val request = Request.Builder()
            .url(baseUrl)
            .method("MKCOL", null)
            .header("Authorization", authorization)
            .build()
        return execute(request) { Unit }
    }

    private fun propfindRequest(depth: String): Request = Request.Builder()
        .url(baseUrl)
        .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA))
        .header("Depth", depth)
        .header("Authorization", authorization)
        .build()

    /**
     * OkHttp 的 execute() 是阻塞调用，必须切到 IO 线程。
     * 之前直接在主线程调用，Android 会抛 NetworkOnMainThreadException。
     */
    private suspend fun <T> execute(request: Request, parse: (String) -> T): WebDavResult<T> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful || response.code == 207) {
                        WebDavResult.Success(parse(body))
                    } else {
                        WebDavResult.Error(describe(response.code), response.code)
                    }
                }
            } catch (e: IOException) {
                WebDavResult.Error("网络错误：${e.message ?: e.javaClass.simpleName}")
            } catch (e: IllegalArgumentException) {
                // 地址不合法时 OkHttp 抛的是运行时异常，之前漏了这一类
                WebDavResult.Error("地址不合法：${e.message.orEmpty()}")
            } catch (e: Exception) {
                WebDavResult.Error("请求失败：${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun describe(code: Int): String = when (code) {
        401 -> "认证失败，请检查账号与密码（坚果云需要用「第三方应用密码」）"
        403 -> "服务器拒绝访问（HTTP 403）"
        404 -> "目录不存在，请检查目录地址"
        405 -> "服务器不支持该操作，可能不是 WebDAV 服务"
        409 -> "父目录不存在，无法创建目录"
        423 -> "资源被锁定，请稍后重试"
        507 -> "云端空间不足"
        else -> "请求失败（HTTP $code）"
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val XML_MEDIA = "application/xml; charset=utf-8".toMediaType()

        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""

        /** 目录地址补上结尾的斜杠，多个斜杠也只在末尾保留一个。 */
        fun normalize(baseUrl: String): String {
            var trimmed = baseUrl.trim()
            if (trimmed.isEmpty()) return ""
            // 用户经常只填域名，没写协议；不补的话 OkHttp 会当成 http://，在 https 服务上会失败
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                trimmed = "https://$trimmed"
            }
            return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}
