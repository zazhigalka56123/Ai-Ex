package ru.itmo.aiex.ingest.service.parsing

object ChatText {
    private const val BOM = '﻿'
    private val DIRECTION_MARKS = Regex("[‎‏‪-‮⁦-⁩]")

    fun decode(content: ByteArray): String = String(content, Charsets.UTF_8).trimStart(BOM)

    fun lines(content: ByteArray): List<String> = decode(content).lines().map(::stripMarks)

    fun stripMarks(line: String): String = line.replace(DIRECTION_MARKS, "")
}
