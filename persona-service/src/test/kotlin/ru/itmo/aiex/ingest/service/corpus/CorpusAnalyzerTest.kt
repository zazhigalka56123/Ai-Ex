package ru.itmo.aiex.ingest.service.corpus

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.ingest.entity.ImportSource
import ru.itmo.aiex.ingest.entity.MessageAuthor
import ru.itmo.aiex.ingest.entity.MessageAuthor.ME
import ru.itmo.aiex.ingest.entity.MessageAuthor.THEM
import ru.itmo.aiex.ingest.service.parsing.TelegramJsonParser
import ru.itmo.aiex.ingest.testing.Fixtures
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

class CorpusAnalyzerTest {
    private val analyzer = CorpusAnalyzer()
    private val t0 = Instant.parse("2024-01-01T10:00:00Z")

    private fun msg(author: MessageAuthor, body: String, minutes: Long?, hour: Int? = null) =
        CorpusMessage(author, body, minutes?.let { t0.plusSeconds(it * 60) }, hour ?: minutes?.let { (10 + it / 60).toInt() % 24 })

    private fun analyze(messages: List<CorpusMessage>, attachments: Int = 0) =
        analyzer.analyze(CorpusInput(UUID.randomUUID(), ImportSource.WHATSAPP_TXT, "Маша", messages, attachments))

    @Test
    fun `статистика на маленьком корпусе считается точно`() {
        val snapshot =
            analyze(
                listOf(
                    msg(ME, "привет", 0),
                    msg(THEM, "привет!", 2),
                    msg(THEM, "ТЫ ГДЕ", 3),
                    msg(ME, "дома", 10),
                    msg(THEM, "ну ок... люблю тебя ❤️😂", 40),
                    msg(THEM, "с кем ты?", 41, hour = 2),
                    msg(ME, "спокойной ночи", 12 * 60),
                    msg(THEM, "Спокойной", 13 * 60),
                ),
                attachments = 2,
            )

        assertThat(snapshot.source).isEqualTo("WHATSAPP_TXT")
        assertThat(snapshot.theirName).isEqualTo("Маша")
        assertThat(snapshot.totalMessages).isEqualTo(8)
        assertThat(snapshot.theirMessages).isEqualTo(5)
        assertThat(snapshot.myMessages).isEqualTo(3)
        assertThat(snapshot.attachments).isEqualTo(2)
        assertThat(snapshot.periodStart).isEqualTo(t0)
        assertThat(snapshot.periodEnd).isEqualTo(t0.plusSeconds(13 * 3600))

        val stats = snapshot.stats

        assertThat(stats.avgMessageLength).isEqualTo(10.8)
        assertThat(stats.medianMessageLength).isEqualTo(9)
        assertThat(stats.emojiPerMessage).isEqualTo(0.4)
        assertThat(stats.topEmojis).containsExactly("❤", "😂")
        assertThat(stats.capsShare).isEqualTo(0.2)
        assertThat(stats.lowercaseStartShare).isEqualTo(0.6)
        assertThat(stats.questionShare).isEqualTo(0.2)
        assertThat(stats.exclamationShare).isEqualTo(0.2)
        assertThat(stats.ellipsisShare).isEqualTo(0.2)
        assertThat(stats.nightShare).isEqualTo(0.2)
        assertThat(stats.hourHistogram).hasSize(24)
        assertThat(stats.hourHistogram.sum()).isEqualTo(5)
        assertThat(stats.hourHistogram[2]).isEqualTo(1)
        assertThat(stats.avgReplyDelaySeconds).isEqualTo((120L + 1800 + 3600) / 3)
        assertThat(stats.initiativeShare).isEqualTo(0.0)
        assertThat(stats.jealousyMarkers).isEqualTo(1)
        assertThat(stats.affectionMarkers).isEqualTo(1)
        assertThat(stats.topWords).contains("привет", "люблю", "спокойной")
    }

    @Test
    fun `маркеры ищутся с начала слова и без учёта регистра`() {
        val bodies = listOf("ГДЕ ТЫ был", "где-то там", "зайти не могу", "зайка моя", "приревновала", "ревную", "Котик", "скучаю")
        assertThat(Markers.countMessages(bodies, Markers.JEALOUSY)).isEqualTo(2)
        assertThat(Markers.countMessages(bodies, Markers.AFFECTION)).isEqualTo(3)
    }

    @Test
    fun `характерные фразы - без повторов, без персональных данных, не длиннее 120 символов`() {
        val snapshot =
            analyze(
                listOf(
                    msg(THEM, "неа", 1),
                    msg(THEM, "НЕА", 2),
                    msg(THEM, "пиши на masha@example.com", 3),
                    msg(THEM, "мой номер +7 (999) 123-45-67", 4),
                    msg(THEM, "смотри https://t.me/x", 5),
                    msg(THEM, "заходи на vk.com/masha", 6),
                    msg(THEM, "код 123456", 7),
                    msg(THEM, "это @masha_real", 8),
                    msg(THEM, "a", 9),
                    msg(THEM, "x".repeat(121), 10),
                    msg(THEM, "ну   ок\nпока", 11),
                ),
            )
        assertThat(snapshot.samplePhrases).containsExactly("неа", "ну ок пока")
    }

    @Test
    fun `фраз больше двенадцати - выбор детерминированный и равномерный`() {
        val messages = (0 until 30).map { msg(THEM, "фраза номер $it", it.toLong()) }
        val first = analyze(messages).samplePhrases
        val second = analyze(messages).samplePhrases
        assertThat(first).hasSize(CorpusAnalyzer.SAMPLE_PHRASES).isEqualTo(second)
        assertThat(first.first()).isEqualTo("фраза номер 0")
        assertThat(first.last()).isEqualTo("фраза номер 27")
    }

    @Test
    fun `без таймстемпов - задержка неизвестна, ночь не считается, разговор один`() {
        val stats =
            analyze(
                listOf(msg(THEM, "привет", null), msg(ME, "привет", null), msg(THEM, "как ты", null)),
            ).stats
        assertThat(stats.avgReplyDelaySeconds).isNull()
        assertThat(stats.nightShare).isEqualTo(0.0)
        assertThat(stats.hourHistogram.sum()).isZero()
        assertThat(stats.initiativeShare).isEqualTo(1.0)
    }

    @Test
    fun `пауза больше суток - не ответ`() {
        val stats = analyze(listOf(msg(ME, "ау", 0), msg(THEM, "я тут", 25 * 60))).stats
        assertThat(stats.avgReplyDelaySeconds).isNull()
        assertThat(stats.initiativeShare).isEqualTo(0.5)
    }

    @Test
    fun `пустой корпус не ломает анализатор`() {
        val snapshot = analyze(emptyList())
        assertThat(snapshot.stats.avgMessageLength).isEqualTo(0.0)
        assertThat(snapshot.stats.medianMessageLength).isZero()
        assertThat(snapshot.samplePhrases).isEmpty()
        assertThat(snapshot.periodStart).isNull()
    }

    @Test
    fun `эмодзи - по диапазонам Unicode, ZWJ-последовательность и тон кожи считаются одним`() {
        assertThat(Emoji.extract("👍🏽 ок 👨‍👩‍👧 ⭐ ✨ ❤️ abc")).containsExactly("👍", "👨", "⭐", "✨", "❤")
        assertThat(Emoji.isEmoji('A'.code)).isFalse()
        assertThat(PrivacyFilter.isSafe("обычная фраза 2024")).isTrue()
    }

    @Test
    fun `выгрузка Telegram из фикстуры - числа совпадают с ручным подсчётом`() {
        val chat = TelegramJsonParser(JsonMapper.builder().build()).parse(Fixtures.bytes("telegram/personal_chat.json"))
        val messages = AuthorResolver.normalize(chat, "Маша")
        val snapshot = analyzer.analyze(CorpusInput(UUID.randomUUID(), ImportSource.TELEGRAM_JSON, "Маша", messages, chat.attachments))

        assertThat(snapshot.theirMessages).isEqualTo(15)
        assertThat(snapshot.myMessages).isEqualTo(10)
        assertThat(snapshot.attachments).isEqualTo(3)
        with(snapshot.stats) {
            assertThat(avgMessageLength).isEqualTo(15.2)
            assertThat(avgReplyDelaySeconds).isEqualTo(7780)
            assertThat(nightShare).isEqualTo(0.6)
            assertThat(initiativeShare).isEqualTo(0.5)
            assertThat(jealousyMarkers).isEqualTo(2)
            assertThat(affectionMarkers).isEqualTo(1)
            assertThat(topEmojis).containsExactly("😂", "❤", "🙂")
        }

        assertThat(snapshot.samplePhrases).hasSize(12).contains("неа", "с кем?", "ГДЕ ТЫ БЫЛ ВЧЕРА").noneMatch { "http" in it || "+7" in it }
    }
}
