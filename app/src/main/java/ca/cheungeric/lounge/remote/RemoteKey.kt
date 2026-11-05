package ca.cheungeric.lounge.remote

import java.security.SecureRandom

/** Pairing key: 4 words from [WordList] joined by '-' (SPEC "v2 → Pairing key"). Pure. */
object RemoteKey {
    const val WORDS = 4
    const val PAIRING_BASE = "https://cheungeric02.github.io/lounge/remote/"

    private val SHAPE = Regex("^[a-z]{3,6}(-[a-z]{3,6}){${WORDS - 1}}$")
    private val random by lazy { SecureRandom() }

    fun generate(rng: java.util.Random = random): String =
        (1..WORDS).joinToString("-") { WordList.words[rng.nextInt(WordList.words.size)] }

    fun isValid(key: String?): Boolean =
        key != null && SHAPE.matches(key) && key.split('-').all { WordList.contains(it) }

    /**
     * Accepts a bare key (any case / surrounding whitespace) or a pairing URL containing `#k=<key>`
     * (or `k=` among `&`-separated fragment params). Returns the canonical key, or null if invalid.
     */
    fun normalize(input: String?): String? {
        if (input == null) return null
        var s = input.trim()
        val hash = s.indexOf('#')
        if (hash >= 0) {
            val frag = s.substring(hash + 1)
            s = frag.split('&').firstNotNullOfOrNull { p ->
                val eq = p.indexOf('=')
                if (eq > 0 && p.substring(0, eq).trim().equals("k", ignoreCase = true)) p.substring(eq + 1) else null
            } ?: return null
        }
        s = s.trim().lowercase()
        return s.takeIf { isValid(it) }
    }

    fun pairingUrl(key: String): String = "$PAIRING_BASE#k=$key"
}
