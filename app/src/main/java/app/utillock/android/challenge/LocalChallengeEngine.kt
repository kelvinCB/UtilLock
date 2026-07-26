package app.utillock.android.challenge

import app.utillock.android.model.LogicChallenge
import java.util.UUID
import java.util.Locale
import kotlin.random.Random

object LocalChallengeEngine {
    private data class Template(val locale: String, val pseudocode: String, val answer: String)

    private val templates = listOf(
        Template(
            "es",
            "x = 3\ny = 1\nPARA i DESDE 1 HASTA 4:\n    y = y * x\n    x = x - 1\nIMPRIMIR y",
            "0",
        ),
        Template(
            "es",
            "suma = 0\nPARA n EN [2, 5, 8, 11]:\n    SI n MOD 2 == 0:\n        suma = suma + n\nIMPRIMIR suma",
            "10",
        ),
        Template(
            "es",
            "a = 1\nb = 1\nREPETIR 5 VECES:\n    c = a + b\n    a = b\n    b = c\nIMPRIMIR b",
            "13",
        ),
        Template(
            "es",
            "contador = 0\nPARA i DESDE 1 HASTA 12:\n    SI i MOD 3 == 0:\n        contador = contador + 1\nIMPRIMIR contador",
            "4",
        ),
        Template(
            "es",
            "valor = 20\nMIENTRAS valor > 3:\n    valor = valor - 4\nIMPRIMIR valor",
            "0",
        ),
        Template(
            "en",
            "total = 0\nFOR n IN [2, 5, 8, 11]:\n    IF n MOD 2 == 0:\n        total = total + n\nPRINT total",
            "10",
        ),
        Template(
            "en",
            "a = 1\nb = 1\nREPEAT 5 TIMES:\n    c = a + b\n    a = b\n    b = c\nPRINT b",
            "13",
        ),
        Template(
            "en",
            "count = 0\nFOR i FROM 1 TO 12:\n    IF i MOD 3 == 0:\n        count = count + 1\nPRINT count",
            "4",
        ),
    )

    fun create(now: Long = System.currentTimeMillis()): LogicChallenge {
        val locale = if (Locale.getDefault().language == "es") "es" else "en"
        val candidates = templates.filter { it.locale == locale }
        val selected = candidates[Random.nextInt(candidates.size)]
        return LogicChallenge(
            id = UUID.randomUUID().toString(),
            title = if (locale == "es") "¿Qué valor imprime el algoritmo?" else "What value does the algorithm print?",
            pseudocode = selected.pseudocode,
            expectedAnswer = selected.answer,
            expiresAtEpochMs = now + 10 * 60_000L,
            remote = false,
        )
    }

    fun normalizeAnswer(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9.-]"), "")
}
