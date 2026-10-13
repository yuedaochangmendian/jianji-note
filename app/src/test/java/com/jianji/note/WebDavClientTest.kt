package com.jianji.note

import com.jianji.note.net.WebDavClient
import com.jianji.note.net.WebDavResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 用本地假服务端把 WebDavClient 完整跑一遍。
 * 请求方法、Depth 头、Basic 认证、状态码分支、失败重试都能验证到，
 * 不需要真机也不需要真实账号。
 */
class WebDavClientTest {

    private lateinit var server: FakeHttpServer

    private var putCount = 0
    private var putFailFirst = false
    private var propfindCode = 207
    private var getBody = "{\"app\":\"jianji\"}"

    @Before
    fun setUp() {
        putCount = 0
        putFailFirst = false
        propfindCode = 207
        server = FakeHttpServer { request ->
            when (request.method) {
                "PROPFIND" -> FakeHttpServer.Reply(propfindCode, MULTISTATUS)
                "MKCOL" -> FakeHttpServer.Reply(201)
                "GET" -> FakeHttpServer.Reply(200, getBody, "application/json")
                "PUT" -> {
                    putCount++
                    FakeHttpServer.Reply(if (putFailFirst && putCount == 1) 409 else 201)
                }
                else -> FakeHttpServer.Reply(405)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun client() =
        WebDavClient("http://127.0.0.1:${server.port}/dav/", "user", "pass")

    @Test
    fun `测试连接发 PROPFIND 并带上 Basic 认证`() {
        val result = runBlocking { client().testConnection() }

        assertTrue("期望成功，实际 $result", result is WebDavResult.Success)
        val request = server.requests.single()
        assertEquals("PROPFIND", request.method)
        assertEquals("0", request.header("Depth"))
        val auth = request.header("Authorization")
        assertTrue("没有带认证头：$auth", auth != null && auth.startsWith("Basic "))
    }

    @Test
    fun `上传把内容 PUT 到目标文件`() {
        val result = runBlocking { client().upload("jianji-backup.json", """{"a":1}""") }

        assertTrue("期望成功，实际 $result", result is WebDavResult.Success)
        val request = server.requests.single()
        assertEquals("PUT", request.method)
        assertEquals("/dav/jianji-backup.json", request.path)
        assertEquals("""{"a":1}""", request.body)
    }

    @Test
    fun `下载返回服务端原文`() {
        val result = runBlocking { client().download("jianji-backup.json") }

        assertTrue("期望成功，实际 $result", result is WebDavResult.Success)
        assertEquals(getBody, (result as WebDavResult.Success).data)
    }

    @Test
    fun `列出目录用 Depth 1 并解析出文件条目`() {
        val result = runBlocking { client().listFiles() }

        assertTrue("期望成功，实际 $result", result is WebDavResult.Success)
        val entries = (result as WebDavResult.Success).data
        // 响应里包含目录自身那条，解析器原样返回；WebDavSync 那层才过滤掉目录
        assertEquals(2, entries.size)
        assertEquals(1, entries.count { it.isDirectory })
        val files = entries.filter { !it.isDirectory }
        assertEquals(1, files.size)
        assertEquals("jianji-backup.json", files[0].name)
        assertEquals("1", server.requests.single().header("Depth"))
    }

    @Test
    fun `认证失败给出可读提示而不是未知错误`() {
        propfindCode = 401

        val result = runBlocking { client().testConnection() }

        assertTrue("期望失败，实际 $result", result is WebDavResult.Error)
        val error = result as WebDavResult.Error
        assertEquals(401, error.httpCode)
        assertTrue("提示不够清楚：${error.message}", error.message.contains("认证失败"))
    }

    @Test
    fun `目录不存在时上传会先 MKCOL 建目录再重试`() {
        putFailFirst = true

        val result = runBlocking { client().upload("jianji-backup.json", "x") }

        assertTrue("期望重试后成功，实际 $result", result is WebDavResult.Success)
        assertEquals(2, putCount)
        assertEquals(
            listOf("PUT", "MKCOL", "PUT"),
            server.requests.map { it.method },
        )
    }

    @Test
    fun `带中文的目录地址能正确请求`() {
        val result = runBlocking {
            WebDavClient("http://127.0.0.1:${server.port}/dav/笔记/", "user", "pass")
                .upload("备份.json", "x")
        }

        assertTrue("期望成功，实际 $result", result is WebDavResult.Success)
        val path = server.requests.single().path
        // OkHttp 会把非 ASCII 按 UTF-8 百分号编码
        assertTrue("路径没有正确编码：$path", path.contains("%E7%AC%94%E8%AE%B0"))
    }

    @Test
    fun `没写协议的地址会补上 https 并补斜杠`() {
        assertEquals("https://dav.example.com/dav/", WebDavClient.normalize("dav.example.com/dav"))
        assertEquals(
            "https://dav.jianguoyun.com/dav/",
            WebDavClient.normalize("  https://dav.jianguoyun.com/dav  "),
        )
        assertEquals("", WebDavClient.normalize("   "))
    }

    private companion object {
        val MULTISTATUS = """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/dav/</D:href>
    <D:propstat>
      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
  <D:response>
    <D:href>/dav/jianji-backup.json</D:href>
    <D:propstat>
      <D:prop>
        <D:getcontentlength>15</D:getcontentlength>
        <D:getlastmodified>Tue, 01 Oct 2024 03:05:00 GMT</D:getlastmodified>
        <D:resourcetype/>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>"""
    }
}
