package com.jianji.note

import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 极简 HTTP/1.1 服务端，只服务于测试 WebDAV 客户端。
 * 一次连接处理一个请求，响应带 Connection: close，OkHttp 会为下个请求重新建连。
 * 不用 MockWebServer 是为了不引入额外依赖（本机拉不到 Google Maven）。
 */
class FakeHttpServer(
    @Volatile var handler: (Request) -> Reply,
) {

    data class Request(
        val method: String,
        val path: String,
        val body: String,
        private val headers: Map<String, String>,
    ) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    data class Reply(
        val code: Int,
        val body: String = "",
        val contentType: String = "application/xml; charset=utf-8",
    )

    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = serverSocket.localPort

    val requests = CopyOnWriteArrayList<Request>()

    fun start() {
        Thread({
            while (!serverSocket.isClosed) {
                try {
                    serverSocket.accept().use { handle(it) }
                } catch (e: Exception) {
                    return@Thread
                }
            }
        }, "fake-http").apply { isDaemon = true }.start()
    }

    fun close() {
        runCatching { serverSocket.close() }
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        val head = readHead(input) ?: return

        val lines = head.split("\r\n")
        val requestLine = lines.first().split(" ")
        val method = requestLine.getOrElse(0) { "" }
        val path = requestLine.getOrElse(1) { "/" }

        val headers = lines.drop(1).mapNotNull { line ->
            val index = line.indexOf(':')
            if (index <= 0) null
            else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
        }.toMap()

        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) String(readExactly(input, length), Charsets.UTF_8) else ""

        val request = Request(method, path, body, headers)
        requests += request

        val reply = handler(request)
        val bodyBytes = reply.body.toByteArray(Charsets.UTF_8)
        val headBytes = buildString {
            append("HTTP/1.1 ${reply.code} ${reason(reply.code)}\r\n")
            append("Content-Type: ${reply.contentType}\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.US_ASCII)

        socket.getOutputStream().apply {
            write(headBytes)
            write(bodyBytes)
            flush()
        }
    }

    /** 逐字节读到空行为止——不能用 BufferedReader，它会把正文一起缓走。 */
    private fun readHead(input: InputStream): String? {
        val builder = StringBuilder()
        var tail = ""
        while (true) {
            val byte = input.read()
            if (byte < 0) return null
            val char = byte.toChar()
            builder.append(char)
            tail = (tail + char).takeLast(4)
            if (tail == "\r\n\r\n") return builder.dropLast(4).toString()
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(data, read, length - read)
            if (count < 0) break
            read += count
        }
        return data
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        207 -> "Multi-Status"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        409 -> "Conflict"
        else -> "Status"
    }
}
