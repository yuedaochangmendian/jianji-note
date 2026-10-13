package com.jianji.note.net

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 解析 WebDAV 的 PROPFIND 响应（207 Multi-Status）。
 *
 * 放在这里独立成对象，一是为了能用 JVM 单元测试覆盖，二是避免把 XML 细节
 * 混进网络层。用 DOM 而不是 android.util.Xml，后者在单元测试里拿不到。
 */
object WebDavParser {

    data class RemoteEntry(
        val href: String,
        val name: String,
        val size: Long,
        val lastModified: Long?,
        val isDirectory: Boolean,
    )

    fun parseMultiStatus(xml: String): List<RemoteEntry> {
        if (xml.isBlank()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Android 的解析器不一定支持这个 feature，不支持就跳过
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val document = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val responses = document.getElementsByTagNameNS("*", "response")
        val entries = ArrayList<RemoteEntry>(responses.length)
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val href = response.firstText("href") ?: continue
            entries += RemoteEntry(
                href = href,
                name = fileNameOf(href),
                size = response.firstText("getcontentlength")?.toLongOrNull() ?: -1L,
                lastModified = response.firstText("getlastmodified")?.let(::parseHttpDate),
                isDirectory = response.getElementsByTagNameNS("*", "collection").length > 0,
            )
        }
        return entries
    }

    /** 从 href 里取最后一段作为文件名，并做 URL 解码（中文名通常是百分号编码）。 */
    fun fileNameOf(href: String): String {
        val trimmed = href.trim().trimEnd('/')
        val index = trimmed.lastIndexOf('/')
        val raw = if (index >= 0) trimmed.substring(index + 1) else trimmed
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    /** 解析 HTTP 日期（RFC 1123 / RFC 3339），失败返回 null。 */
    fun parseHttpDate(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz" to null,
            "EEE, dd MMM yyyy HH:mm:ss" to TimeZone.getTimeZone("GMT"),
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to TimeZone.getTimeZone("GMT"),
        )
        for ((pattern, zone) in patterns) {
            // SimpleDateFormat 不是线程安全的，每次新建
            val format = SimpleDateFormat(pattern, Locale.US)
            if (zone != null) format.timeZone = zone
            val parsed = runCatching { format.parse(value) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    private fun Element.firstText(localName: String): String? {
        val nodes = getElementsByTagNameNS("*", localName)
        for (i in 0 until nodes.length) {
            val text = nodes.item(i).textContent
            if (!text.isNullOrBlank()) return text.trim()
        }
        return null
    }
}
