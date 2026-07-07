package com.example.posmobile.print

import java.io.ByteArrayOutputStream

/**
 * Minimal ESC/POS command builder for thermal receipt printers.
 * [cols] is characters per line (32 for 58 mm, 48 for 80 mm).
 */
class EscPos(private val cols: Int = 32) {
    private val out = ByteArrayOutputStream()

    init { out.write(byteArrayOf(0x1B, 0x40)) } // ESC @ — initialize

    fun align(a: Align): EscPos = cmd(0x1B, 0x61, a.code)
    fun bold(on: Boolean): EscPos = cmd(0x1B, 0x45, if (on) 1 else 0)

    /** Double width+height for headings; GS ! n. */
    fun bigSize(on: Boolean): EscPos = cmd(0x1D, 0x21, if (on) 0x11 else 0x00)

    fun text(s: String): EscPos { out.write(s.toByteArray(Charsets.ISO_8859_1)); return this }
    fun line(s: String = ""): EscPos = text(s).newline()
    fun newline(): EscPos { out.write(0x0A); return this }

    /** Full-width separator, e.g. "--------". */
    fun rule(ch: Char = '-'): EscPos = line(ch.toString().repeat(cols))

    /** Left text and right text on one line, padded to [cols]. Wraps long left text. */
    fun cols(left: String, right: String): EscPos {
        val rightW = right.length
        val leftW = (cols - rightW - 1).coerceAtLeast(1)
        if (left.length <= leftW) {
            val pad = cols - left.length - right.length
            return line(left + " ".repeat(pad.coerceAtLeast(1)) + right)
        }
        // Left too long: print wrapped left, then the amount on its own aligned line.
        wrap(left, cols)
        return line(" ".repeat((cols - rightW).coerceAtLeast(0)) + right)
    }

    /** Word-wrap plain text to [width] columns. */
    fun wrap(s: String, width: Int = cols): EscPos {
        var remaining = s.trim()
        if (remaining.isEmpty()) return this
        while (remaining.length > width) {
            var cut = remaining.lastIndexOf(' ', width)
            if (cut <= 0) cut = width
            line(remaining.substring(0, cut).trimEnd())
            remaining = remaining.substring(cut).trimStart()
        }
        return line(remaining)
    }

    fun feed(n: Int): EscPos { repeat(n) { newline() }; return this }

    /** Feed and full cut (GS V 66 0). Harmless on printers without a cutter. */
    fun cut(): EscPos { feed(3); out.write(byteArrayOf(0x1D, 0x56, 66, 0)); return this }

    fun bytes(): ByteArray = out.toByteArray()

    private fun cmd(vararg b: Int): EscPos { for (x in b) out.write(x); return this }

    enum class Align(val code: Int) { LEFT(0), CENTER(1), RIGHT(2) }
}
