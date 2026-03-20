package com.example.pipelineclean

object ASRCorrector {

    // Common ASR confusion map
    private val correctionMap = mapOf(
        "he want" to "i want",
        "he wants" to "i want",
        "she want" to "i want",
        "the want" to "i want",
        "travel plain" to "travel plan",
        "traval plan" to "travel plan",
        "water bottle" to "water",
        "two" to "to",
        "too" to "to",
        "their" to "there",
        "there is you" to "where are you",
        "what is you doing" to "what are you doing"
    )

    fun correct(input: String): String {

        var text = input.lowercase().trim()

        // Remove filler words
        text = text.replace(Regex("\\b(um|uh|ah|er)\\b"), "")

        // Remove duplicate spaces
        text = text.replace(Regex("\\s+"), " ")

        // Apply phrase-level corrections
        for ((wrong, correct) in correctionMap) {
            if (text.contains(wrong)) {
                text = text.replace(wrong, correct)
            }
        }

        // Capitalize first letter
        text = text.replaceFirstChar { it.uppercase() }

        return text.trim()
    }
}
