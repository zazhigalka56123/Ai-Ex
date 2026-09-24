package ru.itmo.aiex.ingest.domain.parsing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.UnsupportedImportFormatException
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.testing.Fixtures

class FormatDetectorTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "telegram/personal_chat.json, TELEGRAM_JSON",
        "telegram/broken.json, TELEGRAM_JSON",
        "whatsapp/android_ru.txt, WHATSAPP_TXT",
        "whatsapp/android_en.txt, WHATSAPP_TXT",
        "whatsapp/ios.txt, WHATSAPP_TXT",
        "plain/chat.txt, PLAIN_TEXT",
    )
    fun `формат определяется по имени и первым строкам`(path: String, expected: ImportSource) {
        assertThat(FormatDetector.detect(path.substringAfterLast('/'), Fixtures.bytes(path))).isEqualTo(expected)
    }

    @Test
    fun `JSON распознаётся по ведущей скобке даже без расширения, BOM не мешает`() {
        assertThat(FormatDetector.detect("export", "﻿  {\"messages\": []}".toByteArray())).isEqualTo(ImportSource.TELEGRAM_JSON)
        assertThat(FormatDetector.detect(null, "31.12.2023, 23:41 - Маша: привет".toByteArray())).isEqualTo(ImportSource.WHATSAPP_TXT)
    }

    @Test
    fun `PDF, архив и нераспознанный текст - 415 UNSUPPORTED_FORMAT`() {
        listOf(
            "document.pdf" to Fixtures.bytes("other/document.pdf"),
            "chat.zip" to byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00),
            "chat.txt" to byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00),
            "photo.png" to "not really png".toByteArray(),
            "binary.bin" to byteArrayOf(0x01, 0x00, 0x02, 0x03),
            "notes.txt" to Fixtures.bytes("other/notes.txt"),
            "empty.txt" to "   \n\n".toByteArray(),
        ).forEach { (name, content) ->
            assertThatThrownBy { FormatDetector.detect(name, content) }
                .describedAs(name)
                .isInstanceOf(UnsupportedImportFormatException::class.java)
                .hasFieldOrPropertyWithValue("code", ErrorCode.UNSUPPORTED_FORMAT)
        }
    }

    @Test
    fun `расширение файла`() {
        assertThat(FormatDetector.extensionOf("Result.JSON")).isEqualTo("json")
        assertThat(FormatDetector.extensionOf("noext")).isNull()
        assertThat(FormatDetector.extensionOf(null)).isNull()
    }
}
