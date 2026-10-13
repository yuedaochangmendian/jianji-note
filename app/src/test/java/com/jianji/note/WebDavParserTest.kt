package com.jianji.note

import com.jianji.note.net.WebDavClient
import com.jianji.note.net.WebDavParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WebDavParserTest {

    /** 模仿坚果云返回的 207 Multi-Status 结构。 */
    private val multiStatus = """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>/dav/</D:href>
    <D:propstat>
      <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
  <D:response>
    <D:href>/dav/%E7%AC%94%E8%AE%B0/</D:href>
    <D:propstat>
      <D:prop>
        <D:displayname>笔记</D:displayname>
        <D:resourcetype><D:collection/></D:resourcetype>
        <D:getlastmodified>Tue, 01 Oct 2024 03:00:00 GMT</D:getlastmodified>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
  <D:response>
    <D:href>/dav/jianji-backup.json</D:href>
    <D:propstat>
      <D:prop>
        <D:displayname>jianji-backup.json</D:displayname>
        <D:getcontentlength>2048</D:getcontentlength>
        <D:getlastmodified>Tue, 01 Oct 2024 03:05:00 GMT</D:getlastmodified>
        <D:resourcetype/>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>"""

    @Test
    fun `解析多状态响应并区分目录与文件`() {
        val entries = WebDavParser.parseMultiStatus(multiStatus)

        assertEquals(3, entries.size)

        assertTrue(entries[0].isDirectory)
        assertEquals("dav", entries[0].name)

        // 目录名是 URL 编码的中文，需要解码
        assertTrue(entries[1].isDirectory)
        assertEquals("笔记", entries[1].name)
        assertNotNull(entries[1].lastModified)

        val file = entries[2]
        assertFalse(file.isDirectory)
        assertEquals("jianji-backup.json", file.name)
        assertEquals(2048L, file.size)
        assertNotNull(file.lastModified)
    }

    @Test
    fun `空响应或非法内容不会抛异常`() {
        assertEquals(0, WebDavParser.parseMultiStatus("").size)
        assertEquals(0, WebDavParser.parseMultiStatus("<not-a-multistatus/>").size)
    }

    @Test
    fun `HTTP 日期解析`() {
        val millis = WebDavParser.parseHttpDate("Tue, 01 Oct 2024 03:05:00 GMT")
        assertNotNull(millis)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT")).apply {
            timeInMillis = millis!!
        }
        assertEquals(2024, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(3, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, calendar.get(Calendar.MINUTE))

        assertNull(WebDavParser.parseHttpDate(""))
        assertNull(WebDavParser.parseHttpDate("不是日期"))
    }

    @Test
    fun `目录地址统一补斜杠并正确拼接文件名`() {
        assertEquals(
            "https://dav.jianguoyun.com/dav/",
            WebDavClient.normalize("https://dav.jianguoyun.com/dav"),
        )
        assertEquals(
            "https://dav.jianguoyun.com/dav/",
            WebDavClient.normalize("  https://dav.jianguoyun.com/dav/  "),
        )
        assertEquals("", WebDavClient.normalize("   "))

        val client = WebDavClient("https://dav.jianguoyun.com/dav", "user", "pass")
        assertEquals(
            "https://dav.jianguoyun.com/dav/jianji-backup.json",
            client.fileUrl("jianji-backup.json"),
        )
    }
}
