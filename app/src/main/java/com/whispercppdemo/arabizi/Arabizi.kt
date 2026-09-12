package com.whispercppdemo.arabizi

/**
 * Arabic (Levantine) → Arabizi conversion, shared by the main app and the
 * accessibility voice-typing service. Lives on its own so any component in
 * the process can convert Whisper's Arabic output.
 */
object Arabizi {

    val wordDictionary = mapOf(
        // Added from the previous top-level snippet
        "بروعي" to "bro",
        "أنا" to "ana",
        "حليوم" to "lyom",
        "مني حليوم" to "mnih lyom",

        // Latin Hallucinations
        "هي بروكي فك" to "hi bro kifak",
        "بروكي فك" to "bro kifak",
        "بروكي فق" to "bro kifak",
        // q8_0-era spellings: the model fuses "hi bro" into one word
        "هايبرو كيفك" to "hi bro kifak", "هيبرو كيفك" to "hi bro kifak",
        "هايبرو" to "hi bro", "هيبرو" to "hi bro",
        "je" to "khaye",

        // Prefixed Variations
        "وخيي" to "w khaye",
        "ياخيي" to "ya khaye",
        "باوكي" to "b ok",
        "وبونجور" to "w bonjour",
        "عكيفك" to "3a kifak",
        "وكيفك" to "w kifak",

        // Standard Dictionary
        "هاي" to "hi", "حيحة" to "hi khaye", "بمجور" to "bonjour",
        "كيفك" to "kifak", "يكيفك" to "kifak", "كيفكن" to "kifkoun", "كيفكم" to "kifkoun",
        "خيي" to "khaye", "خي" to "khay", "خيه" to "khaye", "حيية" to "khaye",
        "شو" to "chou", "وينك" to "waynak", "وينن" to "waynon",
        "ايه" to "eh", "لأ" to "la2", "لا" to "la", "مش" to "mech",
        "كتير" to "ktir", "منيح" to "mni7", "يلا" to "yalla",
        "هلا" to "hala", "أهلين" to "ahlayn", "حبيبي" to "habibi",
        "عنجد" to "3anjad", "والله" to "walla", "يعني" to "ya3ni",
        "طيب" to "tayeb", "خلاص" to "khalas", "خلص" to "khalas",

        // Names & phrases. Keep these AFTER the general words above: fuzzy matching
        // is first-match-wins, and e.g. منير/منيح are one edit apart, so the
        // everyday word must be scanned before the name.
        "مونير" to "Mounir", "منير" to "Mounir",
        // ASR variants of "جا التلميذة على الغرفة" (misheard ق↔ت, ص↔غ) + correct spelling
        "جا التلميذة" to "ja2a al telmizo", "جاوت تلقميذو" to "ja2a al telmizo",
        "على الغرفة" to "2la al gorfa", "إلى الصفة" to "2la al gorfa",
        "عالغرفة" to "2la al gorfa", "للغرفة" to "2la al gorfa", "للصفة" to "2la al gorfa",
        "الغرفة" to "al gorfa", "الصفة" to "al gorfa",
        "التلميذة" to "al telmizo", "تلقميذو" to "al telmizo",

        // Corpus-mined (72 simulated Lebanese utterances, sim/): spellings the
        // model actually emits for common dialect words. Without these they fall
        // through to letter-mapping (كتير -> "kthyr" instead of "ktir").
        "هيدا" to "haida", "هاد" to "haida",
        "مين" to "meen", "بدي" to "baddi", "بدك" to "baddak", "بدنا" to "baddna",
        "ليك" to "leek", "ليكي" to "leeki", "ليش" to "lesh", "قديش" to "2addesh",
        "كثير" to "ktir", "عاجبك" to "3ajbak", "عجبك" to "3ajbak",
        "شفتو" to "shfto", "شفتوا" to "shfto", "اللي" to "illi", "مبروك" to "mabrouk",
        "ايمتا" to "emtaa", "ايمتى" to "emtaa", "ايمت" to "emtaa", "انوي" to "ano",
        // hamza-less spellings the model emits for existing keys above
        "انا" to "ana", "اهلين" to "ahlayn",
        "تيكل" to "tekol",

        // Arabicized loanwords — Lebanese rides on French/English mid-sentence, and
        // in AR mode the model renders them in Arabic script. Without entries they
        // degrade to letter-mapping (ستايل -> "stayl", ليكران -> "lykran").
        // --- French ---
        "بونجور" to "bonjour", "بونسوار" to "bonsoir", "ميرسي" to "merci", "ميرسي كتير" to "merci ktir",
        "باردين" to "pardon", "سلو" to "salut", "بيسو" to "bisou", "بيسوس" to "bisous",
        "ايكول" to "ecole", "إيكول" to "ecole", "ليسيه" to "lycee",
        "ليكران" to "ecran", "الليكران" to "ecran",
        "ديركشن" to "direction", "تلازة" to "terrasse", "ديكور" to "deco",
        "شوفاج" to "chauffage", "بريز" to "prise", "كراج" to "garage",
        "فاكتور" to "facture", "فاكتورة" to "facture", "ريسو" to "recu",
        "ريونيون" to "reunion", "كونترا" to "contrat", "كونطرا" to "contrat",
        "غاركون" to "garcon", "فارماسي" to "pharmacie", "سينما" to "cinema",
        "شانس" to "chance",
        "قازوز" to "gazouz", "كرواسون" to "croissant", "شوكولا" to "chocolat",
        "فريت" to "frites", "بيتزا" to "pizza", "ساندويش" to "sandwich", "سانديويتش" to "sandwich",
        // --- English ---
        "ستايل" to "style", "سمايل" to "smile", "نايس" to "nice", "كريزي" to "crazy",
        "باي" to "bye", "اوكي" to "ok", "سوري" to "sorry", "بليز" to "please",
        "ميساج" to "message", "كول" to "call", "كولي" to "call",
        "ستارت" to "start", "ستوب" to "stop", "فولو" to "follow",
        "لايك" to "like", "شير" to "share", "سيلفي" to "selfie", "ستوري" to "story",
        "فيسبوك" to "facebook", "واتساب" to "whatsapp", "واتس اب" to "whatsapp", "انستا" to "insta",
        "زووم" to "zoom", "ميتينغ" to "meeting", "ابديت" to "update", "أبديت" to "update",
        "فايل" to "file", "فيلم" to "film", "كليب" to "clip", "فيديو" to "video",
        "موبايل" to "mobile", "تلفون" to "telephone", "كابل" to "cable",
        "كارت" to "carte", "ريشارج" to "recharge", "ريشارجي" to "recharge", "شارجي" to "charge",
        "مول" to "mall", "باركينغ" to "parking", "ترافيك" to "traffic", "طرافيك" to "traffic",
        "سيكوريتي" to "security", "مانيجر" to "manager", "بوس" to "boss",
        "بيبي" to "baby", "فرند" to "friend", "هاني" to "honey",
        // multi-word loans
        "غود مورنينغ" to "good morning", "سيم كارت" to "sim card"
    )

    val charMappings = mapOf(
        "؟" to "?",
        "أ" to "2", "إ" to "2", "ؤ" to "2", "ئ" to "2", "ء" to "2", "ق" to "2",
        "ع" to "3", "خ" to "5", "ط" to "6", "ح" to "7", "غ" to "8",
        "ص" to "9", "ض" to "9'", "ش" to "ch", "ج" to "j",
        "ث" to "th", "ذ" to "z", "ظ" to "z",
        "ا" to "a", "ب" to "b", "ت" to "t", "د" to "d", "ر" to "r",
        "ز" to "z", "س" to "s", "ف" to "f", "ك" to "k",
        "ل" to "l", "م" to "m", "ن" to "n", "ه" to "h", "و" to "w",
        "ي" to "y", "ى" to "a", "ة" to "e"
    )

    private val tashkeelRegex = Regex("[\\u064B-\\u0652\\u0670\\u0640]")

    /** Strip tashkeel and edge punctuation so e.g. "مرحباً" hits key "مرحبا". */
    private fun lookupForm(word: String): String =
        word.replace(tashkeelRegex, "").trim { !it.isLetterOrDigit() }

    private fun calculateDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // Deletion
                    dp[i][j - 1] + 1,       // Insertion
                    dp[i - 1][j - 1] + cost // Substitution
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun findBestFuzzyMatch(input: String, maxDistance: Int): String? {
        // Guards derived from 72 simulated utterances (sim/): fuzzy matching
        // short words corrupts them (في -> خي "khay", هاد -> هاي "hi"), and a
        // much longer/shorter key must never match. Callers try exact hits first.
        if (input.length < 4) return null
        for ((arabicKey, arabiziValue) in wordDictionary) {
            if (Math.abs(arabicKey.length - input.length) > maxDistance) continue
            if (calculateDistance(input, arabicKey) <= maxDistance) {
                return arabiziValue
            }
        }
        return null
    }

    fun containsArabicScript(s: String) = s.any { it in '\u0600'..'\u06FF' }

    /** True when some 3..6-word phrase occurs 3+ times — the Whisper silence loop. */
    fun looksLikeHallucinationLoop(text: String): Boolean {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 9) return false
        for (n in 3..6) {
            val counts = HashMap<String, Int>()
            for (i in 0..words.size - n) {
                val gram = words.subList(i, i + n).joinToString(" ")
                val c = (counts[gram] ?: 0) + 1
                if (c >= 3) return true
                counts[gram] = c
            }
        }
        return false
    }

    /** Arabic-script input → Arabizi; non-Arabic text passes through untouched. */
    fun convert(rawText: String): String {
        if (!containsArabicScript(rawText)) return rawText.trim()
        val cleanedText = rawText.replace("\"", "").replace("؟", "")
        // keep only words containing letters (drops punctuation-only tokens like "..")
        val words = cleanedText.split(" ")
            .filter { it.isNotEmpty() && it.any { c -> c.isLetterOrDigit() } }
            .map { lookupForm(it) }

        var i = 0
        val finalResult = mutableListOf<String>()

        while (i < words.size) {
            if (i <= words.size - 3) {
                val chunk = "${words[i]} ${words[i + 1]} ${words[i + 2]}"
                val exact3 = wordDictionary[chunk]
                if (exact3 != null) {
                    finalResult.add(exact3)
                    i += 3
                    continue
                }
                val match = findBestFuzzyMatch(chunk, maxDistance = 2)
                if (match != null) {
                    finalResult.add(match)
                    i += 3
                    continue
                }
            }

            if (i <= words.size - 2) {
                val chunk = "${words[i]} ${words[i + 1]}"
                val exact2 = wordDictionary[chunk]
                if (exact2 != null) {
                    finalResult.add(exact2)
                    i += 2
                    continue
                }
                val match = findBestFuzzyMatch(chunk, maxDistance = 1)
                if (match != null) {
                    finalResult.add(match)
                    i += 2
                    continue
                }
            }

            val singleWord = words[i]
            val exact = wordDictionary[singleWord]
            if (exact != null) {
                finalResult.add(exact)
            } else {
                val match = findBestFuzzyMatch(singleWord, maxDistance = 1)
                if (match != null) {
                    finalResult.add(match)
                } else {
                    var letterByLetter = ""
                    for (char in singleWord) {
                        letterByLetter += charMappings[char.toString()] ?: char.toString()
                    }
                    finalResult.add(letterByLetter)
                }
            }
            i++
        }
        return finalResult.joinToString(" ")
    }
}
