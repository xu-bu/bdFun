package com.aritxonly.bdfun.core.domain.cast

import org.junit.Assert.*
import org.junit.Test

class CastProtocolParsersTest {

    @Test
    fun `SSDP 响应解析出 LOCATION 并识别 NVA 服务声明`() {
        val raw = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: http://192.168.1.223:8200/desc.xml\r\n" +
            "ST: urn:app-bilibili-com:service:NirvanaControl:3\r\n" +
            "USN: uuid:abc-123::urn:app-bilibili-com:service:NirvanaControl:3\r\n\r\n"
        val parsed = parseSsdpResponse(raw)
        assertNotNull(parsed)
        assertEquals("http://192.168.1.223:8200/desc.xml", parsed!!.location)
        assertTrue(parsed.isNva)
    }

    @Test
    fun `普通 MediaRenderer 的 SSDP 响应不标记为 NVA`() {
        val raw = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://192.168.1.20:8200/desc.xml\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
            "USN: uuid:abc-123::urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        val parsed = parseSsdpResponse(raw)!!
        assertFalse(parsed.isNva)
    }

    @Test
    fun `非 200 或缺 LOCATION 的响应被拒绝`() {
        assertNull(parseSsdpResponse("HTTP/1.1 404 Not Found\r\nLOCATION: http://x\r\n"))
        assertNull(parseSsdpResponse("HTTP/1.1 200 OK\r\nST: ssdp:all\r\n"))
        assertNull(parseSsdpResponse(""))
    }

    @Test
    fun `设备描述含 NirvanaControl 服务时识别为 NVA`() {
        val xml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>客厅的小电视 &amp; 盒子</friendlyName>
                <manufacturer>Bilibili Inc.</manufacturer>
                <modelName>Cloud TV</modelName>
                <UDN>uuid:device-9f8e</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/AVTransport</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:app-bilibili-com:service:NirvanaControl:3</serviceType>
                    <controlURL>NirvanaControl/action</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent()
        val parsed = parseCastDescription(xml)
        assertNotNull(parsed)
        assertEquals("uuid:device-9f8e", parsed!!.udn)
        assertEquals("客厅的小电视 & 盒子", parsed.name)
        assertEquals("Cloud TV", parsed.modelName)
        assertTrue(parsed.isNva)
    }

    @Test
    fun `仅 manufacturer 含 bilibili 也识别为 NVA`() {
        val xml = """
            <root><device><UDN>uuid:mb-1</UDN>
              <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
              <manufacturer>Bilibili</manufacturer>
              <friendlyName>小电视</friendlyName>
            </device></root>
        """.trimIndent()
        assertTrue(parseCastDescription(xml)!!.isNva)
    }

    @Test
    fun `普通 DLNA 渲染设备不带 NVA 标识`() {
        val xml = """
            <root><device><UDN>uuid:dlna-1</UDN>
              <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
              <manufacturer>Example</manufacturer>
              <friendlyName>客厅电视</friendlyName>
              <serviceList><service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/upnp/control/AVTransport</controlURL>
              </service></serviceList>
            </device></root>
        """.trimIndent()
        assertFalse(parseCastDescription(xml)!!.isNva)
    }

    @Test
    fun `LOCATION 提取 host，兼容带端口与不带端口`() {
        assertEquals("192.168.1.223", extractHost("http://192.168.1.223:8200/desc.xml"))
        assertEquals("192.168.1.20", extractHost("http://192.168.1.20/desc.xml"))
        assertEquals("tv.local", extractHost("http://tv.local/desc.xml"))
        assertNull(extractHost("not a url"))
    }

    @Test
    fun `带命名空间前缀的标签也能解析`() {
        val xml = "<root xmlns:d=\"urn:x\"><d:device><d:UDN>uuid:ns-1</d:UDN>" +
            "<d:friendlyName>前缀设备</d:friendlyName>" +
            "<d:deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</d:deviceType>" +
            "<d:manufacturer>Bilibili Inc.</d:manufacturer></d:device></root>"
        val parsed = parseCastDescription(xml)
        assertNotNull(parsed)
        assertEquals("uuid:ns-1", parsed!!.udn)
        assertEquals("前缀设备", parsed.name)
        assertTrue(parsed.isNva)
    }

    @Test
    fun `非渲染设备或缺 UDN 的描述被拒绝`() {
        val notRenderer = "<root><device><UDN>uuid:x</UDN><friendlyName>路由器</friendlyName>" +
            "<deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType></device></root>"
        assertNull(parseCastDescription(notRenderer))

        val noUdn = "<root><device><friendlyName>X</friendlyName>" +
            "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType></device></root>"
        assertNull(parseCastDescription(noUdn))
    }
}
