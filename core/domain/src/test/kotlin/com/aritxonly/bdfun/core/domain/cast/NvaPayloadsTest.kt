package com.aritxonly.bdfun.core.domain.cast

import com.aritxonly.bdfun.core.model.video.DanmakuItem
import org.junit.Assert.*
import org.junit.Test

class NvaPayloadsTest {

    @Test
    fun `Play payload 带齐身份解析与清晰度字段`() {
        val json = nvaPlayPayload(aid = 123L, cid = 456L, quality = 120, seekSec = 10, accessKey = "tk")
        assertTrue(json.contains("\"aid\":\"123\""))
        assertTrue(json.contains("\"cid\":\"456\""))
        assertTrue(json.contains("\"oid\":\"123\""))
        assertTrue(json.contains("\"epId\":\"0\""))
        assertTrue(json.contains("\"contentType\":\"0\""))
        assertTrue(json.contains("\"qn\":120"))
        assertTrue(json.contains("\"userDesireQn\":120"))
        assertTrue(json.contains("\"seekTs\":10"))
        assertTrue(json.contains("\"accessKey\":\"tk\""))
        // 云视听 1.8.9 靠这些字段进入内容身份解析路径 / 判定秒单位
        assertTrue(json.contains("\"autoNext\":\"true\""))
        assertTrue(json.contains("\"an\":true"))
        assertTrue(json.contains("\"isOpen\":\"true\""))
        assertTrue(json.contains("\"io\":true"))
        assertTrue(json.contains("\"mobileVersion\":\"6240000\""))
        assertTrue(json.contains("\"mv\":6240000"))
        assertTrue(json.contains("\"proj_type\":\"1\""))
        // 旧的 snake_case / 猜错的类型必须消失
        assertFalse(json.contains("access_key"))
        assertFalse(json.contains("content_type"))
        assertFalse(json.contains("current_qn"))
    }

    @Test
    fun `弹幕内容里的引号与换行被转义`() {
        val item = DanmakuItem(timeMs = 1000, mode = 1, color = 16777215, size = 1f,
            text = "他说\"你好\"\n换行", id = 42)
        val json = nvaSendDanmakuPayload(item)
        assertTrue(json.contains("\"content\":\"他说\\\"你好\\\"\\n换行\""))
        assertTrue(json.contains("\"mRemoteDmId\":42"))
        assertTrue(json.contains("\"type\":1"))
        assertTrue(json.contains("\"size\":18"))
        assertTrue(json.contains("\"color\":16777215"))
    }

    @Test
    fun `弹幕模式映射到 NVA type 且无 id 时用负数`() {
        val top = nvaSendDanmakuPayload(DanmakuItem(500, 5, 0, 2f, "顶部", id = 0))
        assertTrue(top.contains("\"type\":5"))
        assertTrue(top.contains("\"size\":36"))
        assertTrue(top.contains("\"mRemoteDmId\":-501"))

        val bottom = nvaSendDanmakuPayload(DanmakuItem(500, 4, 0, 1.5f, "底部", id = 9))
        assertTrue(bottom.contains("\"type\":4"))
        assertTrue(bottom.contains("\"size\":25"))
    }

    @Test
    fun `解析 OnProgress 与 OnPlayState`() {
        assertEquals(14 to 91, parseNvaProgress("""{"duration":91,"position":14}"""))
        assertEquals(0 to 0, parseNvaProgress(null))
        assertEquals(4, parseNvaPlayState("""{"playState":4}"""))
        assertNull(parseNvaPlayState("""{"other":1}"""))
    }

    @Test
    fun `解析 OnQnSwitch 的实际生效清晰度与支持列表`() {
        // 云视听 1.8.8 真机抓帧：curQn=80 即 1080P 高清，不是旧的 qn→label 猜测
        val frame = """{"curQn":80,"supportQnList":[{"description":"4K 超高清","displayDesc":"4K","needLogin":false,"needVip":true,"quality":120,"superscript":""},{"description":"1080P 60帧","displayDesc":"1080P","needLogin":false,"needVip":true,"quality":116,"superscript":"60帧"},{"description":"1080P 高清","displayDesc":"1080P","needLogin":false,"needVip":false,"quality":80,"superscript":""},{"description":"720P 准高清","displayDesc":"720P","needLogin":false,"needVip":false,"quality":64,"superscript":""}],"userDesireQn":0}"""
        val info = parseNvaQuality(frame)!!
        assertEquals(80, info.curQn)
        assertEquals(4, info.options.size)
        assertEquals(NvaQualityOption(80, "1080P", "", false), info.options[2])
        assertEquals(NvaQualityOption(116, "1080P", "60帧", true), info.options[1])
        assertTrue(info.options[0].needVip) // 4K 需大会员
    }

    @Test
    fun `解析 OnEpisodeSwitch 里嵌套的 qnDesc`() {
        val frame = """{"playItem":{"aid":1,"cid":2},"qnDesc":{"curQn":64,"supportQnList":[{"displayDesc":"720P","quality":64,"needVip":false}],"userDesireQn":0},"title":"x"}"""
        val info = parseNvaQuality(frame)!!
        assertEquals(64, info.curQn)
        assertEquals(listOf(NvaQualityOption(64, "720P")), info.options)
    }

    @Test
    fun `画质帧缺字段时安全降级`() {
        assertNull(parseNvaQuality(null))
        assertNull(parseNvaQuality("""{"playState":4}"""))
        val onlyCur = parseNvaQuality("""{"curQn":80}""")!!
        assertEquals(80, onlyCur.curQn)
        assertTrue(onlyCur.options.isEmpty())
    }

    @Test
    fun `Seek 与清晰度等遥控 payload`() {
        assertEquals("""{"seekTs":12}""", nvaSeekPayload(12))
        assertEquals("""{"qn":"120"}""", nvaQualityPayload(120))
        assertEquals("""{"speed":"1.5"}""", nvaSpeedPayload("1.5"))
        assertEquals("""{"open":"true"}""", nvaDanmakuSwitchPayload(true))
        assertEquals("""{"volume":30}""", nvaVolumePayload(30))
    }
}
