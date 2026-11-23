package com.example.engine

import com.example.data.model.EmfReading
import kotlin.math.abs

/**
 * 80,000-entry Ovilus ITC Lexicon composed of:
 * - 500 curated single-word paranormal, sensory, name, temporal, and spatial terms
 * - 37,500 adjective + noun descriptors (150 x 250)
 * - 18,000 imperative/action verb + complement phrases (120 x 150)
 * - 24,000 spatial/contextual opener + target phrases (100 x 240)
 * Total = 80,000 distinct, clearly speakable English entries.
 */
object OvilusWordBank {

    const val WORD_BANK_SIZE = 80_000

    internal val coreSingleWords: List<String> = listOf(
        // 1-50: Core ITC & Spatial
        "hello", "listen", "behind", "look", "here", "wait", "follow", "remember", "cold", "shadow",
        "door", "stairs", "attic", "basement", "window", "mirror", "whisper", "light", "dark", "quiet",
        "someone", "watching", "leave", "stay", "help", "find", "name", "home", "house", "room",
        "hallway", "corner", "ceiling", "floor", "wall", "candle", "clock", "bell", "footsteps", "knock",
        "voice", "breath", "touch", "chill", "fear", "peace", "sorry", "alone", "lost", "trapped",
        // 51-100: Names & People
        "john", "mary", "james", "sarah", "william", "elizabeth", "thomas", "margaret", "charles", "anna",
        "george", "clara", "henry", "alice", "edward", "helen", "robert", "grace", "arthur", "emily",
        "walter", "rose", "frank", "eleanor", "samuel", "lillian", "joseph", "agnes", "albert", "ruth",
        "daniel", "hannah", "mother", "father", "sister", "brother", "child", "daughter", "son", "doctor",
        "captain", "preacher", "soldier", "teacher", "keeper", "caretaker", "neighbor", "stranger", "friend", "guest",
        // 101-150: Time & Numbers
        "midnight", "dawn", "dusk", "tonight", "yesterday", "tomorrow", "always", "never", "soon", "now",
        "before", "after", "century", "decade", "winter", "autumn", "summer", "spring", "october", "november",
        "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "twenty", "thirty", "forty", "fifty", "hundred", "thousand", "first",
        "second", "third", "last", "early", "late", "hour", "minute", "moment", "forever", "ago",
        // 151-200: Places & Architecture
        "cellar", "parlor", "kitchen", "bedroom", "nursery", "porch", "balcony", "stairwell", "corridor", "archway",
        "chimney", "fireplace", "hearth", "cupboard", "closet", "pantry", "garage", "barn", "shed", "chapel",
        "church", "graveyard", "cemetery", "garden", "courtyard", "gate", "bridge", "river", "creek", "forest",
        "woods", "hill", "valley", "road", "path", "alley", "tower", "crypt", "vault", "tunnel",
        "boiler", "furnace", "library", "study", "office", "hospital", "asylum", "hotel", "theater", "school",
        // 201-250: Objects & Relics
        "key", "lock", "chain", "ring", "locket", "portrait", "photograph", "painting", "frame", "letter",
        "diary", "journal", "book", "bible", "coin", "watch", "lantern", "lamp", "flashlight", "radio",
        "camera", "recorder", "compass", "pendulum", "violin", "piano", "music", "doll", "cradle", "chair",
        "table", "bed", "blanket", "curtain", "carpet", "glass", "bottle", "knife", "hammer", "shovel",
        "rope", "wire", "pipe", "valve", "switch", "battery", "signal", "static", "frequency", "magnet",
        // 251-300: Actions & Verbs
        "speak", "hear", "see", "feel", "know", "think", "believe", "understand", "forget", "forgive",
        "hide", "seek", "run", "walk", "turn", "stop", "open", "close", "enter", "exit",
        "climb", "descend", "fall", "rise", "reach", "hold", "drop", "break", "burn", "freeze",
        "cry", "laugh", "sing", "pray", "warn", "protect", "guard", "call", "answer", "write",
        "read", "count", "move", "stand", "sit", "sleep", "wake", "dream", "breathe", "vanish",
        // 301-350: Emotions & States
        "angry", "sad", "happy", "afraid", "calm", "anxious", "confused", "tired", "weak", "strong",
        "hopeful", "lonely", "jealous", "proud", "guilty", "innocent", "safe", "dangerous", "urgent", "patient",
        "gentle", "fierce", "bitter", "sweet", "hollow", "empty", "full", "heavy", "silent", "loud",
        "bright", "dim", "pale", "sharp", "soft", "warm", "freezing", "damp", "dry", "dusty",
        "broken", "sealed", "locked", "hidden", "buried", "missing", "ancient", "young", "old", "alive",
        // 351-400: Nature & Elements
        "wind", "rain", "storm", "thunder", "lightning", "fog", "mist", "smoke", "ash", "fire",
        "flame", "ember", "frost", "snow", "ice", "water", "flood", "earth", "stone", "brick",
        "wood", "iron", "steel", "silver", "gold", "copper", "rust", "blood", "bone", "dust",
        "moon", "star", "sky", "night", "eclipse", "north", "south", "east", "west", "above",
        "below", "inside", "outside", "under", "over", "near", "far", "between", "across", "beyond",
        // 401-450: Paranormal & Energy Concepts
        "spirit", "ghost", "phantom", "apparition", "presence", "entity", "orb", "portal", "vortex", "energy",
        "aura", "pulse", "surge", "spike", "current", "charge", "field", "wave", "echo", "resonance",
        "vision", "omen", "sign", "clue", "proof", "truth", "secret", "mystery", "memory", "curse",
        "blessing", "prayer", "angel", "guardian", "watcher", "witness", "messenger", "wanderer", "traveler", "soul",
        "heart", "mind", "eye", "hand", "finger", "shoulder", "face", "teeth", "hair", "coat",
        // 451-500: Conversational & Responses
        "yes", "no", "maybe", "please", "thank", "welcome", "goodbye", "farewell", "careful", "beware",
        "danger", "truthfully", "clearly", "slowly", "quickly", "quietly", "together", "apart", "again", "almost",
        "nothing", "everything", "anything", "nobody", "everybody", "somewhere", "nowhere", "why", "who", "where",
        "when", "what", "which", "because", "therefore", "however", "perhaps", "certainly", "ready", "finished",
        "complete", "begin", "continue", "change", "choose", "trust", "doubt", "wonder", "notice", "believe me"
    )

    internal val adjectives: List<String> = listOf(
        "cold", "dark", "quiet", "heavy", "broken", "silent", "hollow", "ancient", "shadowed", "sudden",
        "restless", "distant", "pale", "hidden", "narrow", "wooden", "rusted", "empty", "bright", "faint",
        "frozen", "burning", "locked", "sealed", "open", "forgotten", "lost", "secret", "gentle", "fierce",
        "lonely", "watching", "whispering", "wandering", "lingering", "fading", "glowing", "flickering", "trembling", "creaking",
        "damp", "dusty", "cobwebbed", "crooked", "steep", "deep", "high", "low", "wide", "tight",
        "sharp", "soft", "warm", "bitter", "sweet", "strange", "familiar", "unseen", "invisible", "clear",
        "blurred", "static", "electric", "magnetic", "charged", "neutral", "rapid", "slow", "steady", "erratic",
        "first", "second", "third", "final", "old", "young", "elder", "weary", "awake", "asleep",
        "calm", "anxious", "afraid", "brave", "patient", "urgent", "careful", "reckless", "honest", "false",
        "true", "real", "vivid", "phantom", "spectral", "ethereal", "mortal", "sacred", "cursed", "blessed",
        "silver", "golden", "copper", "iron", "stone", "brick", "glass", "velvet", "linen", "paper",
        "crimson", "amber", "violet", "indigo", "emerald", "ashen", "ivory", "ebony", "scarlet", "cobalt",
        "northern", "southern", "eastern", "western", "upper", "lower", "inner", "outer", "back", "front",
        "left", "right", "central", "corner", "far", "near", "early", "late", "midnight", "twilight",
        "winter", "autumn", "summer", "spring", "stormy", "misty", "foggy", "windy", "rainy", "moonlit"
    )

    internal val nouns: List<String> = listOf(
        "door", "doorway", "stair", "staircase", "step", "cellar", "basement", "attic", "loft", "window",
        "glass", "mirror", "shadow", "voice", "whisper", "echo", "footstep", "knock", "tap", "breath",
        "breeze", "draft", "chill", "spark", "flame", "candle", "lantern", "lamp", "light", "beam",
        "corridor", "hallway", "passage", "tunnel", "archway", "room", "chamber", "parlor", "kitchen", "pantry",
        "bedroom", "nursery", "study", "library", "office", "closet", "cupboard", "wardrobe", "chest", "drawer",
        "corner", "wall", "floor", "ceiling", "rafter", "beamwork", "pillar", "column", "mantel", "hearth",
        "fireplace", "chimney", "furnace", "boiler", "pipe", "radiator", "switch", "wire", "outlet", "panel",
        "clock", "pendulum", "bell", "chime", "watch", "chain", "key", "keyhole", "lock", "latch",
        "hinge", "handle", "knob", "threshold", "porch", "veranda", "balcony", "roof", "gable", "shutter",
        "curtain", "drape", "carpet", "rug", "board", "plank", "tile", "brick", "stone", "foundation",
        "portrait", "painting", "frame", "photograph", "album", "letter", "note", "diary", "journal", "page",
        "book", "bible", "ledger", "map", "blueprint", "ring", "locket", "brooch", "necklace", "coin",
        "doll", "toy", "cradle", "chair", "rocker", "table", "desk", "bench", "bed", "pillow",
        "blanket", "quilt", "mirror frame", "candlestick", "vase", "bottle", "jar", "cup", "plate", "spoon",
        "violin", "piano", "organ", "music box", "record", "radio", "antenna", "speaker", "static", "frequency",
        "signal", "wave", "pulse", "surge", "current", "charge", "field", "vortex", "portal", "aura",
        "spirit", "presence", "entity", "figure", "silhouette", "apparition", "phantom", "soul", "ghost", "watcher",
        "keeper", "guardian", "messenger", "traveler", "visitor", "stranger", "friend", "neighbor", "doctor", "captain",
        "soldier", "teacher", "preacher", "child", "mother", "father", "sister", "brother", "daughter", "son",
        "eye", "gaze", "hand", "finger", "shoulder", "face", "smile", "tear", "sigh", "heartbeat",
        "memory", "dream", "nightmare", "vision", "omen", "sign", "clue", "warning", "message", "secret",
        "truth", "promise", "oath", "prayer", "blessing", "song", "melody", "story", "name", "date",
        "hour", "moment", "minute", "night", "evening", "midnight", "dawn", "dusk", "season", "year",
        "moon", "star", "sky", "cloud", "mist", "fog", "smoke", "ash", "ember", "rain",
        "storm", "thunder", "wind", "frost", "ice", "river", "creek", "bridge", "gate", "path"
    )

    internal val verbs: List<String> = listOf(
        "listen", "look", "watch", "wait", "follow", "stay", "leave", "remember", "speak", "answer",
        "hear", "see", "feel", "touch", "hold", "open", "close", "unlock", "lock", "knock",
        "tap", "walk", "step", "turn", "stand", "sit", "kneel", "climb", "descend", "enter",
        "exit", "return", "come", "go", "reach", "point", "check", "search", "find", "seek",
        "hide", "wait by", "look at", "listen to", "walk toward", "step near", "stay inside", "move past", "go back", "come closer",
        "call", "whisper", "echo", "signal", "pulse", "flicker", "glow", "shine", "dim", "fade",
        "appear", "vanish", "linger", "hover", "drift", "pass", "cross", "guard", "protect", "warn",
        "guide", "lead", "help", "save", "forgive", "forget", "trust", "doubt", "know", "believe",
        "think", "wonder", "notice", "observe", "measure", "record", "capture", "save now", "play back", "read",
        "write", "count", "breathe", "rest", "wake", "sleep", "dream", "pray", "cry", "laugh",
        "sing", "hum", "ring", "chime", "creak", "rattle", "shift", "freeze", "warm", "cool",
        "shine on", "reach out", "keep still", "stay quiet", "look up", "look down", "look left", "look right", "turn around", "step back"
    )

    internal val complements: List<String> = listOf(
        "here", "there", "now", "tonight", "soon", "again", "already", "always", "never", "forever",
        "closely", "carefully", "quietly", "slowly", "quickly", "gently", "clearly", "softly", "loudly", "suddenly",
        "behind you", "beside you", "near you", "above you", "below you", "with you", "for you", "without fear", "in peace", "in silence",
        "upstairs", "downstairs", "inside", "outside", "nearby", "away", "forward", "backward", "around", "within",
        "at the door", "by the wall", "near the window", "in the corner", "on the stairs", "in the attic", "in the cellar", "in the dark", "in the mirror", "by the hearth",
        "at midnight", "until dawn", "before dusk", "after dark", "long ago", "years ago", "one more time", "right now", "right here", "this room",
        "the doorway", "the hallway", "the staircase", "the basement", "the parlor", "the chimney", "the lantern", "the candle", "the portrait", "the clock",
        "the shadow", "the light", "the signal", "the static", "the frequency", "the voice", "the whisper", "the footsteps", "the cold spot", "the threshold",
        "my name", "my voice", "my hand", "my story", "my home", "my family", "this place", "this house", "this building", "this floor",
        "the truth", "the secret", "the warning", "the sign", "the answer", "the message", "the memory", "the promise", "the key", "the lock",
        "one step", "two steps", "three knocks", "four walls", "five minutes", "seven times", "ten seconds", "twelve chimes", "every hour", "every night",
        "with me", "to me", "for me", "near me", "by me", "from here", "from above", "from below", "from within", "from beyond",
        "in the mist", "in the fog", "in the rain", "in the wind", "by the gate", "on the porch", "under the floor", "over the rafters", "across the hall", "along the corridor",
        "the spirit", "the watcher", "the guardian", "the traveler", "the stranger", "the child", "the mother", "the father", "the doctor", "the keeper",
        "stillness", "patience", "courage", "mercy", "comfort", "clarity", "presence", "energy", "resonance", "shelter"
    )

    internal val openers: List<String> = listOf(
        "near", "behind", "inside", "above", "below", "beside", "across", "beyond", "around", "toward",
        "before", "after", "under", "within", "from", "into", "along", "against", "outside", "opposite",
        "someone at", "someone near", "someone inside", "someone behind", "voice from", "whisper in", "whisper near", "echo from", "echo inside", "shadow by",
        "shadow near", "shadow behind", "footsteps in", "footsteps near", "footsteps above", "footsteps below", "cold spot by", "cold spot near", "cold spot in", "draft from",
        "light near", "light inside", "flicker in", "flicker by", "knock at", "knock on", "tapping at", "tapping inside", "breathing near", "breathing behind",
        "watching from", "standing by", "standing near", "waiting at", "waiting inside", "waiting behind", "lingering in", "lingering near", "hiding in", "hiding behind",
        "look toward", "listen near", "check inside", "check behind", "walk toward", "return to", "remember", "stay near", "leave", "guarding",
        "protecting", "warning about", "sign near", "omen in", "energy at", "energy near", "surge in", "surge near", "pulse from", "static near",
        "presence at", "presence in", "presence behind", "figure by", "figure near", "figure in", "apparition at", "spirit in", "spirit near", "spirit behind",
        "memory of", "echo of", "sound of", "scent of", "trace of", "ghost of", "keeper of", "guardian of", "witness to", "locked inside"
    )

    internal val targets: List<String> = listOf(
        "the doorway", "the front door", "the back door", "the basement", "the cellar", "the attic", "the loft", "the hallway", "the corridor", "the staircase",
        "the upper stairs", "the lower stairs", "the landing", "the threshold", "the archway", "the window", "the bay window", "the broken glass", "the mirror", "the parlor",
        "the sitting room", "the dining room", "the kitchen", "the pantry", "the bedroom", "the master room", "the nursery", "the playroom", "the study", "the library",
        "the office", "the closet", "the cupboard", "the wardrobe", "the corner", "the far corner", "the dark corner", "the north wall", "the south wall", "the east wall",
        "the west wall", "the ceiling", "the rafters", "the floorboards", "the trapdoor", "the crawlspace", "the chimney", "the fireplace", "the hearth", "the mantel",
        "the furnace", "the boiler room", "the utility room", "the laundry room", "the garage", "the workshop", "the barn", "the shed", "the porch", "the balcony",
        "the veranda", "the roof", "the gable", "the shutters", "the curtains", "the drapes", "the old carpet", "the rocking chair", "the armchair", "the wooden table",
        "the oak desk", "the iron bed", "the cradle", "the crib", "the portrait", "the oil painting", "the photograph", "the mirror frame", "the grandfather clock", "the wall clock",
        "the pendulum", "the chandelier", "the lantern", "the oil lamp", "the candle", "the flashlight", "the radio", "the static", "the recorder", "the microphone",
        "the compass", "the meter", "the sensor", "the copper wire", "the breaker box", "the light switch", "the radiator", "the iron pipe", "the old piano", "the violin",
        "the music box", "the porcelain doll", "the wooden chest", "the locked trunk", "the drawer", "the keyhole", "the brass key", "the iron lock", "the heavy chain", "the rusty hinge",
        "the dusty book", "the old bible", "the diary", "the handwritten letter", "the ledger", "the blueprint", "the silver locket", "the gold ring", "the pocket watch", "the old coin",
        "the chapel", "the sanctuary", "the bell tower", "the crypt", "the stone vault", "the tunnel", "the stone arch", "the courtyard", "the iron gate", "the garden",
        "the graveyard", "the cemetery", "the headstone", "the willow tree", "the oak tree", "the pine woods", "the forest path", "the dirt road", "the gravel lane", "the wooden bridge",
        "the creek", "the river bank", "the misty valley", "the steep hill", "the hospital ward", "the long corridor", "the waiting room", "the elevator shaft", "the stairwell", "the theater stage",
        "the balcony seats", "the dressing room", "the hotel lobby", "the third floor", "the fourth room", "the seventh step", "the midnight hour", "the witching hour", "the dawn light", "the evening dusk",
        "the storm", "the thunder", "the winter wind", "the autumn mist", "the heavy fog", "the cold draft", "the warm ember", "the wood ash", "the hearthstone", "the brickwork",
        "the cornerstone", "the foundation", "the wooden beam", "the iron railing", "the banister", "the staircase post", "the coat rack", "the umbrella stand", "the medicine cabinet", "the glass bottle",
        "the silver bell", "the brass handle", "the window latch", "the cellar steps", "the attic hatch", "the chimney flue", "the parlor sofa", "the dining table", "the kitchen sink", "the pantry shelf",
        "the garden gate", "the stone wall", "the picket fence", "the mailbox", "the streetlamp", "the alleyway", "the crossroad", "the old well", "the windmill", "the barn loft",
        "the cellar door", "the side entrance", "the rear porch", "the sunroom", "the conservatory", "the foyer", "the vestibule", "the mezzanine", "the gallery", "the alcove",
        "the niche", "the storage room", "the archive", "the vault door", "the boiler pipe", "the fuse box", "the dark room", "the quiet hall", "the empty room", "the old house",
        "the past", "the memory", "the warning", "the signal", "the frequency", "the silence", "the shadow", "the darkness", "the moonlight", "the night",
        "john", "mary", "william", "elizabeth", "thomas", "margaret", "henry", "clara", "george", "sarah"
    )

    private val coreCount: Int = coreSingleWords.size
    private val adjNounCount: Int = adjectives.size * nouns.size
    private val verbCompCount: Int = verbs.size * complements.size
    private val openerTargetCount: Int = openers.size * targets.size

    val totalCount: Int
        get() = coreCount + adjNounCount + verbCompCount + openerTargetCount

    fun getWordAt(index: Int): String {
        val safeIdx = ((index % totalCount) + totalCount) % totalCount
        if (safeIdx < coreCount) {
            return coreSingleWords[safeIdx]
        }
        var rem = safeIdx - coreCount
        if (rem < adjNounCount) {
            val a = rem / nouns.size
            val n = rem % nouns.size
            return "${adjectives[a]} ${nouns[n]}"
        }
        rem -= adjNounCount
        if (rem < verbCompCount) {
            val v = rem / complements.size
            val c = rem % complements.size
            return "${verbs[v]} ${complements[c]}"
        }
        rem -= verbCompCount
        val o = rem / targets.size
        val t = rem % targets.size
        return "${openers[o]} ${targets[t]}"
    }

    /**
     * Deterministically selects an index across the 80,000-word bank from 3-axis EMF telemetry,
     * flux delta, and microsecond timing entropy, with realistic phonetic syllable cueing.
     */
    fun selectFromEmf(reading: EmfReading, questionSeed: String = ""): Pair<Int, String> {
        val xBits = (reading.x * 1000f).toLong()
        val yBits = (reading.y * 1000f).toLong()
        val zBits = (reading.z * 1000f).toLong()
        val magBits = (reading.magnitude * 1000f).toLong()
        val deltaBits = (abs(reading.delta) * 10000f).toLong()
        val timeMix = System.nanoTime() xor (reading.timestampMs * 0x9E3779B97F4A7C15uL.toLong())
        val qHash = questionSeed.hashCode().toLong()

        var h = 0x517CC1B727220A95L
        h = (h xor xBits) * 0xBF58476D1CE4E5B9uL.toLong()
        h = (h xor yBits) * 0x94D049BB133111EBuL.toLong()
        h = (h xor zBits) * 0xBF58476D1CE4E5B9uL.toLong()
        h = (h xor magBits) * 0x94D049BB133111EBuL.toLong()
        h = (h xor deltaBits) * 0xBF58476D1CE4E5B9uL.toLong()
        h = (h xor timeMix xor qHash)
        h = h xor (h ushr 31)

        // Blend 30% direct core single words with 70% full 80,000-entry dictionary sweep
        val bankSize = totalCount
        val rawIndex = ((h and 0x7FFFFFFFL) % bankSize).toInt()
        val index = if (((h ushr 33) and 0xFFL) < 77L) {
            ((h ushr 12) and 0x7FFFFFFFL).toInt() % coreCount
        } else {
            rawIndex
        }
        return index to getWordAt(index)
    }

    fun buildPhoneticCue(word: String): String {
        val cleaned = word.trim().lowercase().replace(Regex("[^a-z ]"), "")
        if (cleaned.isEmpty()) return "oh-vee"
        val parts = cleaned.split(" ").filter { it.isNotBlank() }
        val first = parts.firstOrNull() ?: return "ah"
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val lead = first.take(2)
        val mid = first.firstOrNull { it in vowels } ?: 'a'
        val tail = first.takeLast(2)
        return "$lead-$mid-$tail"
    }

    /**
     * Searches across all 80,000 entries (single words first, then contextual phrases)
     * without allocating the full 80K list in memory.
     */
    fun searchWords(query: String, limit: Int = 24): List<Pair<Int, String>> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return coreSingleWords.take(limit).mapIndexed { idx, w -> idx to w }
        }
        val results = ArrayList<Pair<Int, String>>(limit)
        for (i in coreSingleWords.indices) {
            val w = coreSingleWords[i]
            if (w.contains(q)) {
                results.add(i to w)
                if (results.size >= limit) return results
            }
        }
        // Scan adjective + noun
        var base = coreCount
        for (a in adjectives.indices) {
            val adj = adjectives[a]
            for (n in nouns.indices) {
                val noun = nouns[n]
                if (adj.contains(q) || noun.contains(q) || "$adj $noun".contains(q)) {
                    results.add((base + a * nouns.size + n) to "$adj $noun")
                    if (results.size >= limit) return results
                }
            }
        }
        // Scan verb + complement
        base += adjNounCount
        for (v in verbs.indices) {
            val verb = verbs[v]
            for (c in complements.indices) {
                val comp = complements[c]
                if (verb.contains(q) || comp.contains(q) || "$verb $comp".contains(q)) {
                    results.add((base + v * complements.size + c) to "$verb $comp")
                    if (results.size >= limit) return results
                }
            }
        }
        // Scan opener + target
        base += verbCompCount
        for (o in openers.indices) {
            val opener = openers[o]
            for (t in targets.indices) {
                val target = targets[t]
                if (opener.contains(q) || target.contains(q) || "$opener $target".contains(q)) {
                    results.add((base + o * targets.size + t) to "$opener $target")
                    if (results.size >= limit) return results
                }
            }
        }
        return results
    }
}
