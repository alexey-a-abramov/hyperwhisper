package com.hyperwhisper.ui

/**
 * Emoji categories and data
 */
object EmojiData {

    enum class EmojiCategory(val displayName: String, val icon: String) {
        SMILEYS("Smileys", "😀"),
        PEOPLE("People", "👋"),
        ANIMALS("Animals", "🐶"),
        FOOD("Food", "🍕"),
        ACTIVITIES("Activities", "⚽"),
        TRAVEL("Travel", "✈️"),
        OBJECTS("Objects", "💡"),
        SYMBOLS("Symbols", "🔣"),
        FLAGS("Flags", "🏁")
    }

    // Emoji data organized by category
    val emojisByCategory = mapOf(
        EmojiCategory.SMILEYS to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇",
            "🥰", "😍", "🤩", "😘", "😗", "☺️", "😚", "😙", "🥲", "😋", "😛", "😜", "🤪",
            "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒",
            "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮",
            "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕",
            "😟", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥",
            "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠",
            "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖"
        ),

        EmojiCategory.PEOPLE to listOf(
            "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
            "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏",
            "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶",
            "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅", "👄", "💋",
            "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩", "🧓", "👴", "👵", "🙍",
            "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇", "🤦", "🤷", "👮", "🕵️", "💂", "🥷"
        ),

        EmojiCategory.ANIMALS to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷",
            "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥",
            "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
            "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🕸️", "🦂", "🐢", "🐍", "🦎",
            "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋",
            "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫"
        ),

        EmojiCategory.FOOD to listOf(
            "🍕", "🍔", "🍟", "🌭", "🍿", "🧈", "🥓", "🥚", "🍳", "🧇", "🥞", "🧈", "🍖",
            "🍗", "🥩", "🥙", "🌮", "🌯", "🫔", "🥗", "🥘", "🫕", "🥫", "🍝", "🍜", "🍲",
            "🍛", "🍣", "🍱", "🥟", "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢",
            "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬", "🍫", "🍿",
            "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "🍼", "☕", "🫖", "🍵", "🧃", "🥤", "🧋",
            "🍶", "🍺", "🍻", "🥂", "🍷", "🥃", "🍸", "🍹", "🧉", "🍾", "🧊", "🥄", "🍴"
        ),

        EmojiCategory.ACTIVITIES to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸",
            "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋",
            "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸",
            "🤺", "⛹️", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴",
            "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🏵️", "🎫", "🎟️", "🎪", "🤹", "🎭",
            "🩰", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🪘", "🎷", "🎺", "🪗", "🎸"
        ),

        EmojiCategory.TRAVEL to listOf(
            "✈️", "🛫", "🛬", "🪂", "💺", "🚁", "🚟", "🚠", "🚡", "🛰️", "🚀", "🛸", "🚂",
            "🚃", "🚄", "🚅", "🚆", "🚇", "🚈", "🚉", "🚊", "🚝", "🚞", "🚋", "🚌", "🚍",
            "🚎", "🚐", "🚑", "🚒", "🚓", "🚔", "🚕", "🚖", "🚗", "🚘", "🚙", "🛻", "🚚",
            "🚛", "🚜", "🏎️", "🏍️", "🛵", "🦽", "🦼", "🛺", "🚲", "🛴", "🛹", "🛼", "🚏",
            "🛣️", "🛤️", "🛢️", "⛽", "🚨", "🚥", "🚦", "🛑", "🚧", "⚓", "⛵", "🛶", "🚤",
            "🛳️", "⛴️", "🛥️", "🚢", "🗿", "🗽", "🗼", "🏰", "🏯", "🏟️", "🎡", "🎢", "🎠"
        ),

        EmojiCategory.OBJECTS to listOf(
            "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴", "💶", "💷", "🪙", "💰",
            "💳", "💎", "⚖️", "🪜", "🧰", "🪛", "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🪚", "🔩",
            "⚙️", "🪤", "🧱", "⛓️", "🧲", "🔫", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️", "🛡️",
            "🚬", "⚰️", "🪦", "⚱️", "🏺", "🔮", "📿", "🧿", "💈", "⚗️", "🔭", "🔬", "🕳️",
            "🩹", "🩺", "💊", "💉", "🩸", "🧬", "🦠", "🧫", "🧪", "🌡️", "🧹", "🪠", "🧺",
            "🧻", "🪣", "🧼", "🪥", "🧽", "🧴", "🛎️", "🔑", "🗝️", "🚪", "🪑", "🛋️", "🛏️"
        ),

        EmojiCategory.SYMBOLS to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞",
            "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯",
            "🕎", "☯️", "☦️", "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
            "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳", "🈶", "🈚", "🈸",
            "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️", "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️",
            "🅱️", "🆎", "🆑", "🅾️", "🆘", "❌", "⭕", "🛑", "⛔", "📛", "🚫", "💯", "💢"
        ),

        EmojiCategory.FLAGS to listOf(
            "🏁", "🚩", "🎌", "🏴", "🏳️", "🏳️‍🌈", "🏳️‍⚧️", "🏴‍☠️", "🇦🇨", "🇦🇩", "🇦🇪", "🇦🇫",
            "🇦🇬", "🇦🇮", "🇦🇱", "🇦🇲", "🇦🇴", "🇦🇶", "🇦🇷", "🇦🇸", "🇦🇹", "🇦🇺", "🇦🇼", "🇦🇽",
            "🇦🇿", "🇧🇦", "🇧🇧", "🇧🇩", "🇧🇪", "🇧🇫", "🇧🇬", "🇧🇭", "🇧🇮", "🇧🇯", "🇧🇱", "🇧🇲",
            "🇧🇳", "🇧🇴", "🇧🇶", "🇧🇷", "🇧🇸", "🇧🇹", "🇧🇻", "🇧🇼", "🇧🇾", "🇧🇿", "🇨🇦", "🇨🇨",
            "🇨🇩", "🇨🇫", "🇨🇬", "🇨🇭", "🇨🇮", "🇨🇰", "🇨🇱", "🇨🇲", "🇨🇳", "🇨🇴", "🇨🇵", "🇨🇷",
            "🇨🇺", "🇨🇻", "🇨🇼", "🇨🇽", "🇨🇾", "🇨🇿", "🇩🇪", "🇩🇬", "🇩🇯", "🇩🇰", "🇩🇲", "🇩🇴"
        )
    )

    // Searchable emoji map with keywords
    val emojiKeywords = buildMap {
        // Smileys
        put("😀", listOf("smile", "happy", "grin"))
        put("😂", listOf("laugh", "lol", "tears", "joy"))
        put("😍", listOf("love", "heart", "eyes"))
        put("😭", listOf("cry", "sad", "tears"))
        put("😘", listOf("kiss", "love", "heart"))
        put("😊", listOf("smile", "blush", "happy"))
        put("🤔", listOf("think", "thinking", "hmm"))
        put("🥰", listOf("love", "hearts", "smile"))
        put("😎", listOf("cool", "sunglasses"))
        put("😴", listOf("sleep", "tired", "zzz"))
        put("🤗", listOf("hug", "care"))
        put("🤩", listOf("star", "excited", "wow"))

        // People
        put("👋", listOf("wave", "hello", "hi", "bye"))
        put("👍", listOf("thumbs", "up", "like", "good", "yes"))
        put("👎", listOf("thumbs", "down", "dislike", "bad", "no"))
        put("🙏", listOf("pray", "thanks", "please", "namaste"))
        put("💪", listOf("strong", "muscle", "power"))
        put("👏", listOf("clap", "applause", "congrats"))

        // Animals
        put("🐶", listOf("dog", "puppy", "pet"))
        put("🐱", listOf("cat", "kitty", "pet"))
        put("🐻", listOf("bear"))
        put("🐼", listOf("panda", "bear"))
        put("🦁", listOf("lion", "king"))
        put("🐯", listOf("tiger"))
        put("🐸", listOf("frog"))
        put("🐵", listOf("monkey"))

        // Food
        put("🍕", listOf("pizza", "food"))
        put("🍔", listOf("burger", "hamburger", "food"))
        put("🍟", listOf("fries", "french", "food"))
        put("🍕", listOf("pizza", "food"))
        put("☕", listOf("coffee", "tea", "drink", "cafe"))
        put("🍺", listOf("beer", "drink", "alcohol"))
        put("🍰", listOf("cake", "birthday", "dessert"))

        // Activities
        put("⚽", listOf("soccer", "football", "ball", "sport"))
        put("🏀", listOf("basketball", "ball", "sport"))
        put("🎮", listOf("game", "controller", "gaming"))
        put("🎵", listOf("music", "note"))
        put("🎬", listOf("movie", "film", "cinema"))

        // Travel
        put("✈️", listOf("plane", "airplane", "travel", "flight"))
        put("🚗", listOf("car", "auto", "vehicle"))
        put("🚀", listOf("rocket", "space"))
        put("🏠", listOf("home", "house"))

        // Objects
        put("💡", listOf("light", "bulb", "idea"))
        put("📱", listOf("phone", "mobile", "iphone"))
        put("💻", listOf("laptop", "computer", "pc"))
        put("⌚", listOf("watch", "time", "clock"))

        // Symbols
        put("❤️", listOf("heart", "love", "red"))
        put("💔", listOf("broken", "heart", "sad"))
        put("✨", listOf("sparkle", "stars", "shine"))
        put("🔥", listOf("fire", "hot", "lit"))
        put("⭐", listOf("star", "favorite"))
        put("✅", listOf("check", "yes", "done", "correct"))
        put("❌", listOf("x", "no", "wrong", "cancel"))
    }

    /**
     * Search emojis by keyword
     */
    fun searchEmojis(query: String): List<String> {
        if (query.isBlank()) return emptyList()

        val lowerQuery = query.lowercase().trim()
        val results = mutableSetOf<String>()

        // Search in keywords
        emojiKeywords.forEach { (emoji, keywords) ->
            if (keywords.any { it.contains(lowerQuery) }) {
                results.add(emoji)
            }
        }

        // Also search in all emojis if character matches
        emojisByCategory.values.flatten().forEach { emoji ->
            if (emoji.contains(lowerQuery)) {
                results.add(emoji)
            }
        }

        return results.toList()
    }

    /**
     * Get all emojis as a flat list
     */
    fun getAllEmojis(): List<String> {
        return emojisByCategory.values.flatten()
    }
}
