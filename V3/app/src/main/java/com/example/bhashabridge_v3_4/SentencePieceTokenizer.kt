package com.example.bhashabridge_v3_4

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class SentencePieceTokenizer(context: Context, modelAsset: String) {

    private val pieceToId = HashMap<String, Int>(131072)
    private val idToPiece = HashMap<Int, String>(131072)

    private val SP = "\u2581"

    private var srcLangTokenId: Long = 4L
    private var tgtLangTokenId: Long = 15L

    init {
        val dictAsset = when {
            modelAsset.contains("SRC_HI") -> "dict.SRC_HI.json"
            modelAsset.contains("TGT_EN") -> "dict.TGT_EN.json"
            modelAsset.contains("SRC")    -> "dict.SRC.json"
            else                          -> "dict.TGT.json"
        }

        BufferedReader(InputStreamReader(context.assets.open(dictAsset))).use { reader ->
            val sb = StringBuilder()
            var c: Int
            var inString   = false
            var escape     = false
            var key        = ""
            var readingKey = true

            while (reader.read().also { c = it } != -1) {
                val ch = c.toChar()
                if (escape) { sb.append(ch); escape = false; continue }
                when {
                    ch == '\\' && inString  -> { sb.append(ch); escape = true }
                    ch == '"'  && !inString -> { inString = true }
                    ch == '"'  && inString  -> {
                        inString = false
                        if (readingKey) { key = sb.toString(); sb.clear() }
                    }
                    ch == ':'  && !inString -> { readingKey = false; sb.clear() }
                    ch == ','  && !inString -> {
                        val id = sb.toString().trim().toIntOrNull()
                        if (key.isNotEmpty() && id != null) {
                            pieceToId[key] = id
                            idToPiece[id]  = key
                        }
                        key = ""; sb.clear(); readingKey = true
                    }
                    ch == '}'  && !inString -> {
                        val id = sb.toString().trim().toIntOrNull()
                        if (key.isNotEmpty() && id != null) {
                            pieceToId[key] = id
                            idToPiece[id]  = key
                        }
                    }
                    inString                  -> sb.append(ch)
                    ch.isDigit() || ch == '-' -> sb.append(ch)
                }
            }
        }

        when {
            modelAsset.contains("SRC_HI") -> {
                // HI→EN: hin_Deva=8, eng_Latn=4  (from indic-en tok.src_encoder)
                srcLangTokenId = (pieceToId["hin_Deva"] ?: 8).toLong()
                tgtLangTokenId = (pieceToId["eng_Latn"] ?: 4).toLong()
            }
            modelAsset.contains("SRC") -> {
                // EN→HI: eng_Latn=4, hin_Deva=15  (from en-indic tok.src_encoder)
                srcLangTokenId = (pieceToId["eng_Latn"] ?: 4).toLong()
                tgtLangTokenId = (pieceToId["hin_Deva"] ?: 15).toLong()
            }
        }
    }

    fun encode(text: String): LongArray {
        val words = text.trim().split("\\s+".toRegex())
        val ids   = ArrayList<Long>(words.size * 2 + 3)

        // IndicTrans2 format: [src_lang, tgt_lang, subwords..., </s>]
        ids.add(srcLangTokenId)
        ids.add(tgtLangTokenId)

        for (word in words) {
            val lower = word.lowercase()
            val title = lower.replaceFirstChar { it.uppercaseChar() }
            val upper = lower.uppercase()

            val matched = when {
                pieceToId.containsKey("$SP$lower") -> { ids.add(pieceToId["$SP$lower"]!!.toLong()); true }
                pieceToId.containsKey("$SP$title") -> { ids.add(pieceToId["$SP$title"]!!.toLong()); true }
                pieceToId.containsKey("$SP$upper") -> { ids.add(pieceToId["$SP$upper"]!!.toLong()); true }
                else -> false
            }

            if (!matched) ids.addAll(greedyEncode("$SP$lower"))
        }

        ids.add((pieceToId["</s>"] ?: 2).toLong())
        return ids.toLongArray()
    }

    private fun greedyEncode(text: String): List<Long> {
        val unkId  = (pieceToId["<unk>"] ?: 3).toLong()
        val result = ArrayList<Long>(text.length)
        var pos    = 0
        while (pos < text.length) {
            var matched = false
            for (end in minOf(text.length, pos + 20) downTo pos + 1) {
                val sub = text.substring(pos, end)
                if (pieceToId.containsKey(sub)) {
                    result.add(pieceToId[sub]!!.toLong())
                    pos     = end
                    matched = true
                    break
                }
            }
            if (!matched) { result.add(unkId); pos++ }
        }
        return result
    }

    fun decode(ids: List<Long>): String {
        val langTagRegex = Regex("^[a-z]{2,3}_[A-Z][a-z]{3,}$")
        val skip = setOf(0L, 1L, 2L, 3L)
        return ids
            .filter { it !in skip }
            .mapNotNull { idToPiece[it.toInt()] }
            .filter { piece -> !piece.matches(langTagRegex) }
            .joinToString("")
            .replace("\u2581", " ")
            .trim()
    }
}