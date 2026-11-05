package com.magicsmart.core.adb

import java.io.DataInputStream
import java.io.DataOutputStream

/** إطار بروتوكول ADB الكلاسيكي (رأس 24 بايت + بيانات اختيارية). */
internal class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray) {
    companion object {
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_WRTE = 0x45545257
        const val A_CLSE = 0x45534c43
        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3
        const val A_VERSION = 0x01000000
        const val MAX_PAYLOAD = 256 * 1024

        fun le32(v: Int, buf: java.io.ByteArrayOutputStream) {
            buf.write(v and 0xFF); buf.write((v ushr 8) and 0xFF); buf.write((v ushr 16) and 0xFF); buf.write((v ushr 24) and 0xFF)
        }

        fun readLe32(d: DataInputStream): Int {
            val b = ByteArray(4); d.readFully(b)
            return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
        }

        fun read(inp: DataInputStream): AdbMessage {
            val command = readLe32(inp)
            val arg0 = readLe32(inp)
            val arg1 = readLe32(inp)
            val len = readLe32(inp)
            readLe32(inp) // checksum؛ غير مُتحقَّق منه عند الاستقبال
            val magic = readLe32(inp)
            require(magic == command.inv()) { "ADB frame magic mismatch" }
            require(len in 0..MAX_PAYLOAD) { "ADB frame length out of range: $len" }
            val data = if (len > 0) ByteArray(len).also { inp.readFully(it) } else ByteArray(0)
            return AdbMessage(command, arg0, arg1, data)
        }
    }

    fun write(out: DataOutputStream) {
        val checksum = data.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        val head = java.io.ByteArrayOutputStream(24)
        le32(command, head); le32(arg0, head); le32(arg1, head)
        le32(data.size, head); le32(checksum, head); le32(command.inv(), head)
        out.write(head.toByteArray())
        if (data.isNotEmpty()) out.write(data)
        out.flush()
    }

}

internal fun cstr(s: String): ByteArray = (s + "\u0000").toByteArray(Charsets.UTF_8)
