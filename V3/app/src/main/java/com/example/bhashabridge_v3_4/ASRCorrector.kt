package com.example.bhashabridge_v3_4

import android.util.Log

object ASRCorrector {

    private const val TAG = "ASRCORRECTOR"

    private val phraseMap = linkedMapOf(
        // Indian English grammar corrections
        "what is you doing"     to "what are you doing",
        "what is you"           to "what are you",
        "where is you"          to "where are you",
        "how is you"            to "how are you",
        "he want to"            to "i want to",
        "he wants to"           to "i want to",
        "she want to"           to "i want to",
        "the want to"           to "i want to",
        "he want"               to "i want",
        "he wants"              to "i want",
        "she want"              to "i want",
        "the want"              to "i want",
        "i am feel"             to "i am feeling",
        "i is"                  to "i am",
        "travel plain"          to "travel plan",
        "traval plan"           to "travel plan",
        "traval plain"          to "travel plan",
        "i did went"            to "i went",
        "i have went"           to "i have gone",
        "more better"           to "better",
        "more faster"           to "faster",
        "more easier"           to "easier",
        "can you able to"       to "are you able to",
        "i am having hunger"    to "i am hungry",
        "i am having thirst"    to "i am thirsty",
        "i am having fever"     to "i have fever",
        "she go "               to "she goes ",
        "he go "                to "he goes ",
        "it go "                to "it goes ",
        "they goes "            to "they go ",
        "we goes "              to "we go ",
        "i goes "               to "i go ",
        "you goes "             to "you go ",
        "she don't"             to "she doesn't",
        "he don't"              to "he doesn't",
        "it don't"              to "it doesn't",
        "i are "                to "i am ",
        "you is "               to "you are ",
        "they is "              to "they are ",
        "we is "                to "we are ",
        "did you went"          to "did you go",
        "have you went"         to "have you gone",
        "calm the police"       to "call the police",
        "calm an ambulance"     to "call an ambulance",
        "calm a doctor"         to "call a doctor",
        "calm my"               to "call my",
    )

    private val wordMap = mapOf(
        "gonna"   to "going to",
        "wanna"   to "want to",
        "gotta"   to "got to",
        "kinda"   to "kind of",
        "coulda"  to "could have",
        "woulda"  to "would have",
        "shoulda" to "should have",
        "lemme"   to "let me",
        "gimme"   to "give me",
        "dunno"   to "don't know",
        "hafta"   to "have to",
        "tryna"   to "trying to",
        "sorta"   to "sort of",
        "lotta"   to "lot of",
        "outta"   to "out of",
    )

    fun correct(input: String): String {
        if (input.isBlank()) return input

        var text = input.lowercase().trim()
        text = text.replace(Regex("\\b(um|uh|ah|er|hmm|hm|erm)\\b\\s*"), "")
        text = text.replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return input

        for ((wrong, fix) in phraseMap) {
            if (text.contains(wrong)) text = text.replace(wrong, fix)
        }
        for ((wrong, fix) in wordMap) {
            text = text.replace(Regex("\\b${Regex.escape(wrong)}\\b"), fix)
        }
        text = text.replace(Regex("\\s+"), " ").trim()

        text = applyGrammarDisambiguation(text)

        return text.trim().replaceFirstChar { it.uppercase() }
    }

    private val hindiPhraseMap = linkedMapOf(
        "मैं जाना है" to "मुझे जाना है",
        "मैं खाना है" to "मुझे खाना है",
        "मैं पानी है" to "मुझे पानी चाहिए",
    )

    private fun applyGrammarDisambiguation(text: String): String {
        val words = text.trim().split("\\s+".toRegex())
        if (words.isEmpty()) return text

        val first  = words[0].lowercase()
        val second = if (words.size > 1) words[1].lowercase() else ""
        val third  = if (words.size > 2) words[2].lowercase() else ""

        val whereConfusions = setOf("that", "there", "wear", "were", "ware", "value")
        val beVerbs         = setOf("are", "is", "were", "was", "am")
        val pronouns        = setOf("you", "he", "she", "they", "we", "i", "it")

        if (first in whereConfusions && second in beVerbs && third in pronouns) {
            return "where ${words.drop(1).joinToString(" ")}"
        }

        val goingVerbs = setOf("going", "doing", "coming", "saying", "eating",
                               "drinking", "sitting", "standing", "running")
        if (first in whereConfusions && second in pronouns &&
            (third in goingVerbs || third.endsWith("ing"))) {
            return "where are ${words.drop(1).joinToString(" ")}"
        }

        if (first in setOf("then", "than")) {
            return "when ${words.drop(1).joinToString(" ")}"
        }

        if (first in whereConfusions && second in setOf("the", "a", "an")) {
            return "where is ${words.drop(1).joinToString(" ")}"
        }

        return text
    }

    fun correctHindi(input: String): String {
        if (input.isBlank()) return input
        var text = input.trim()
        text = text.replace(Regex("\\s+"), " ").trim()
        for ((wrong, fix) in hindiPhraseMap) {
            if (text.contains(wrong)) text = text.replace(wrong, fix)
        }
        return text
    }

    fun isLanguageToolReady(): Boolean = false
}
