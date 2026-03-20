package com.example.bhashabridge_v3_4

object EmergencyPhrases {

    data class Phrase(val english: String, val hindi: String, val category: Category)

    enum class Category(val label: String, val emoji: String) {
        MEDICAL("Medical", "🏥"), SAFETY("Safety", "🚨"),
        LOCATION("Location", "📍"), BASIC("Basic", "🆘")
    }

    val all: List<Phrase> = listOf(
        Phrase("I need help",                "मुझे मदद चाहिए",           Category.BASIC),
        Phrase("Please help me",             "कृपया मेरी मदद करें",      Category.BASIC),
        Phrase("Emergency",                  "आपातकाल",                  Category.BASIC),
        Phrase("I am in danger",             "मैं खतरे में हूँ",         Category.BASIC),
        Phrase("Stay calm",                  "शांत रहें",                Category.BASIC),
        Phrase("Do not panic",               "घबराएं नहीं",              Category.BASIC),
        Phrase("Call an ambulance",          "एम्बुलेंस बुलाओ",          Category.MEDICAL),
        Phrase("Call a doctor",              "डॉक्टर को बुलाओ",         Category.MEDICAL),
        Phrase("I am injured",               "मैं घायल हूँ",             Category.MEDICAL),
        Phrase("I am bleeding",              "मुझे खून आ रहा है",        Category.MEDICAL),
        Phrase("I cannot breathe",           "मुझे सांस नहीं आ रही",    Category.MEDICAL),
        Phrase("I have chest pain",          "मेरे सीने में दर्द है",    Category.MEDICAL),
        Phrase("He is unconscious",          "वह बेहोश है",              Category.MEDICAL),
        Phrase("I need medicine",            "मुझे दवाई चाहिए",          Category.MEDICAL),
        Phrase("I am diabetic",              "मुझे मधुमेह है",           Category.MEDICAL),
        Phrase("I am allergic",              "मुझे एलर्जी है",           Category.MEDICAL),
        Phrase("Call the police",            "पुलिस को बुलाओ",           Category.SAFETY),
        Phrase("Fire",                       "आग",                       Category.SAFETY),
        Phrase("There is a fire",            "यहाँ आग लगी है",           Category.SAFETY),
        Phrase("Earthquake",                 "भूकंप",                    Category.SAFETY),
        Phrase("Flood",                      "बाढ़",                      Category.SAFETY),
        Phrase("I am lost",                  "मैं खो गया हूँ",           Category.SAFETY),
        Phrase("Get out now",                "अभी बाहर निकलो",           Category.SAFETY),
        Phrase("Run away",                   "भाग जाओ",                  Category.SAFETY),
        Phrase("Do not touch that",          "उसे मत छुओ",               Category.SAFETY),
        Phrase("Where is the hospital",      "अस्पताल कहाँ है",          Category.LOCATION),
        Phrase("Where is the police station","पुलिस स्टेशन कहाँ है",    Category.LOCATION),
        Phrase("Take me to the hospital",    "मुझे अस्पताल ले जाओ",     Category.LOCATION),
        Phrase("What is this place",         "यह जगह क्या है",           Category.LOCATION),
        Phrase("I need water",               "मुझे पानी चाहिए",          Category.LOCATION),
        Phrase("I need food",                "मुझे खाना चाहिए",          Category.LOCATION),
        Phrase("Is there a shelter nearby",  "क्या पास में आश्रय है",    Category.LOCATION),
    )

    fun byCategory(cat: Category): List<Phrase> = all.filter { it.category == cat }
}
