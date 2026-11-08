package com.hdrezka.tv.data

import org.json.JSONObject
import java.security.MessageDigest

data class AnubisChallenge(
    val id: String,
    val randomData: String,
    val difficulty: Int
) {
    fun solve(limit: Int = 20_000_000): Pair<Int, String>? {
        val prefix = "0".repeat(difficulty.coerceAtLeast(0))
        var nonce = 0
        while (nonce < limit) {
            val hex = sha256Hex(randomData + nonce)
            if (hex.startsWith(prefix)) return nonce to hex
            nonce++
        }
        return null
    }

    companion object {
        private val challengeRegex = Regex(
            """<script id="anubis_challenge" type="application/json">(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )

        fun parse(html: String): AnubisChallenge? {
            val json = challengeRegex.find(html)?.groupValues?.getOrNull(1) ?: return null
            return try {
                val root = JSONObject(json)
                val challenge = root.getJSONObject("challenge")
                val rules = root.getJSONObject("rules")
                AnubisChallenge(
                    id = challenge.getString("id"),
                    randomData = challenge.getString("randomData"),
                    difficulty = rules.getInt("difficulty")
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            val out = StringBuilder(digest.size * 2)
            for (byte in digest) {
                val v = byte.toInt() and 0xff
                if (v < 0x10) out.append('0')
                out.append(v.toString(16))
            }
            return out.toString()
        }
    }
}
