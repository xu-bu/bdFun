package com.aritxonly.bdfun.core.domain.cast

import org.junit.Assert.*
import org.junit.Test

class NvaFramesTest {

    /** 把 "e0 02 ..." 形式的 hex 转成字节数组，便于用抓包原文做断言。 */
    private fun hex(s: String): ByteArray = s.trim()
        .split(Regex("\\s+"))
        .map { it.toInt(16).toByte() }
        .toByteArray()

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }

    @Test
    fun `GetVolume 指令编码与抓包字节完全一致`() {
        // 逆向文章抓包：e0 02 00 00 00 01 01 07 'Command' 09 'GetVolume'
        val expected = hex(
            "e0 02 00 00 00 01 01 07 43 6f 6d 6d 61 6e 64 09 " +
                "47 65 74 56 6f 6c 75 6d 65",
        )
        val encoded = encodeNvaCommand(1, "GetVolume")
        assertArrayEquals(expected, encoded)
    }

    @Test
    fun `Play 指令带 JSON payload，长度用大端 4 字节`() {
        val json = """{"aid":"123","cid":"456"}"""
        val encoded = encodeNvaCommand(2, "Play", json)
        // argc=3, seq=2 大端, JSON 长度大端
        assertEquals(0xe0.toByte(), encoded[0])
        assertEquals(3.toByte(), encoded[1])
        assertEquals("00 00 00 02", encoded.copyOfRange(2, 6).toHex())
        assertEquals(0x01.toByte(), encoded[6]) // 固定字节，疑似协议版本 v1
        // Command(7) + Play(4) 之后是 4 字节 JSON 长度
        val jsonLenPos = 7 + 1 + 7 + 1 + 4
        val expectedLen = json.toByteArray(Charsets.UTF_8).size
        assertEquals(
            "%02x %02x %02x %02x".format(expectedLen shr 24 and 0xff, expectedLen shr 16 and 0xff,
                expectedLen shr 8 and 0xff, expectedLen and 0xff),
            encoded.copyOfRange(jsonLenPos, jsonLenPos + 4).toHex(),
        )
        val decoded = NvaFrameDecoder().feed(encoded).single()
        assertEquals(NvaFrame.Command(2, "Play", json), decoded)
    }

    @Test
    fun `解码抓包里的 GetVolume 指令`() {
        val raw = hex(
            "e0 02 00 00 00 01 01 07 43 6f 6d 6d 61 6e 64 09 " +
                "47 65 74 56 6f 6c 75 6d 65",
        )
        assertEquals(NvaFrame.Command(1, "GetVolume", null), NvaFrameDecoder().feed(raw).single())
    }

    @Test
    fun `解码抓包里的 volume 回复帧`() {
        // c0 01 00 00 00 01 00 00 00 0d {"volume":33}
        val raw = hex(
            "c0 01 00 00 00 01 00 00 00 0d 7b 22 76 6f 6c 75 6d 65 22 3a 33 33 7d",
        )
        assertEquals(NvaFrame.Reply(1, """{"volume":33}"""), NvaFrameDecoder().feed(raw).single())
    }

    @Test
    fun `解码空回复与 ping 帧`() {
        assertEquals(NvaFrame.Reply(2, null), NvaFrameDecoder().feed(hex("c0 00 00 00 00 02")).single())
        assertEquals(NvaFrame.Ping(1), NvaFrameDecoder().feed(hex("e4 00 00 00 00 01")).single())
        assertEquals(NvaFrame.Ping(2), NvaFrameDecoder().feed(hex("e4 00 00 00 00 02")).single())
    }

    @Test
    fun `解码抓包里的 OnProgress 状态帧`() {
        // TV 主动推的指令：e0 03 ... Command 0a OnProgress ... {"duration":91,"position":14}
        val raw = hex(
            "e0 03 00 00 00 02 01 07 43 6f 6d 6d 61 6e 64 0a " +
                "4f 6e 50 72 6f 67 72 65 73 73 00 00 00 1d 7b 22 64 75 72 61 74 69 6f 6e " +
                "22 3a 39 31 2c 22 70 6f 73 69 74 69 6f 6e 22 3a 31 34 7d",
        )
        val frame = NvaFrameDecoder().feed(raw).single() as NvaFrame.Command
        assertEquals(2, frame.seq)
        assertEquals("OnProgress", frame.name)
        assertEquals("""{"duration":91,"position":14}""", frame.json)
    }

    @Test
    fun `半包分多次喂入也能正确还原`() {
        val raw = encodeNvaCommand(7, "Seek", """{"seekTs":12}""")
        val decoder = NvaFrameDecoder()
        assertTrue(decoder.feed(raw.copyOfRange(0, 3)).isEmpty())
        assertTrue(decoder.feed(raw.copyOfRange(3, 10)).isEmpty())
        val frames = decoder.feed(raw.copyOfRange(10, raw.size))
        assertEquals(NvaFrame.Command(7, "Seek", """{"seekTs":12}"""), frames.single())
    }

    @Test
    fun `一个 TCP 块里粘多帧能全部拆出`() {
        val a = encodeNvaCommand(1, "Pause")
        val b = hex("e4 00 00 00 00 03")
        val c = encodeNvaReply(4, """{"volume":30}""")
        val combined = a + b + c
        assertEquals(
            listOf(
                NvaFrame.Command(1, "Pause", null),
                NvaFrame.Ping(3),
                NvaFrame.Reply(4, """{"volume":30}"""),
            ),
            NvaFrameDecoder().feed(combined),
        )
    }

    @Test
    fun `编码序号必须为正`() {
        assertThrows(IllegalArgumentException::class.java) { encodeNvaCommand(0, "Play") }
    }
}
