package com.example.wellwisher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WishActivity : AppCompatActivity() {

    private lateinit var contact: Contact
    private var currentWish = ""
    private var localWishIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wish)

        val id = intent.getStringExtra("contact_id") ?: return finish()
        val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        var found: Contact? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                found = Contact(
                    id = o.optString("id"), name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    cat = o.optString("cat", "birthday"),
                    date = o.getString("date"),
                    remindHour = o.optInt("remindHour", 9),
                    remindMin = o.optInt("remindMin", 0),
                    emojiStyle = o.optString("emojiStyle", "few"),
                    wishIndex = o.optInt("wishIndex", 0),
                    personalTouch = o.optString("personalTouch", "")
                )
            }
        }
        contact = found ?: return finish()
        localWishIndex = contact.wishIndex

        val icon = if (contact.cat == "anniversary") "💑" else "🎂"
        val catLabel = if (contact.cat == "anniversary") "Anniversary" else "Birthday"
        findViewById<TextView>(R.id.tvWishIcon).text = icon
        findViewById<TextView>(R.id.tvWishName).text = contact.name
        findViewById<TextView>(R.id.tvWishMeta).text = catLabel

        generateAndShowWish()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            localWishIndex++
            generateAndShowWish()
            saveWishIndex()
        }
        findViewById<ImageButton>(R.id.btnWhatsApp).setOnClickListener { openWhatsApp() }
        findViewById<ImageButton>(R.id.btnCopy).setOnClickListener { copyWish() }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { shareWish() }
        findViewById<ImageButton>(R.id.btnEdit).setOnClickListener {
            val tv = findViewById<TextView>(R.id.tvWishMessage)
            tv.isEnabled = true
            tv.isFocusable = true
            tv.isFocusableInTouchMode = true
            tv.requestFocus()
            Toast.makeText(this, "✏️ You can now edit the message directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndShowWish() {
        currentWish = buildWish(contact, localWishIndex)
        val tv = findViewById<TextView>(R.id.tvWishMessage)
        tv.text = currentWish
        tv.isEnabled = false
        tv.isFocusable = false
    }

    private fun saveWishIndex() {
        val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == contact.id) {
                o.put("wishIndex", localWishIndex)
                break
            }
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    private fun openWhatsApp() {
        val msg = findViewById<TextView>(R.id.tvWishMessage).text.toString()
        val phone = contact.phone.replace(Regex("[^0-9+]"), "")
        if (phone.isEmpty()) { shareWish(); return }
        try {
            val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(msg)}")
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            shareWish()
        }
    }

    private fun copyWish() {
        val msg = findViewById<TextView>(R.id.tvWishMessage).text.toString()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wish", msg))
        Toast.makeText(this, "✅ Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun shareWish() {
        val msg = findViewById<TextView>(R.id.tvWishMessage).text.toString()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }
        startActivity(Intent.createChooser(intent, "Share wish via"))
    }

    private fun ordinal(n: Int): String {
        val s = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$s"
    }

    private fun buildWish(c: Contact, idx: Int): String {
        val n = c.name.split(" ")[0]
        val y = try {
            java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - c.date.split("-")[0].toInt()
        } catch (e: Exception) { 0 }

        val list = if (c.cat == "anniversary") anniversaryWishes else birthdayWishes
        var msg = list[idx % list.size]
            .replace("{n}", n)
            .replace("{ordinal}", if (y > 0) ordinal(y) else "")
            .replace("{years}", if (y > 0) y.toString() else "")

        if (c.personalTouch.isNotEmpty()) {
            msg += "\n\n${c.personalTouch}"
        }

        if (c.emojiStyle == "none") {
            msg = msg.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+|[\\u2600-\\u27BF]"), "")
                .replace(Regex("\\s+"), " ").trim()
        } else if (c.emojiStyle == "lots") {
            msg = msg + " 🎊🎈🎁✨💫"
        }

        return msg
    }

    private val birthdayWishes = listOf(
        // 1
        "Happy Birthday {n}! 🎂 Hope today is as incredible as you are. Wishing you a day packed with laughter, love, and everything that makes your heart happy. You deserve only the very best!",
        // 2
        "Hey {n}! 🎉 Another trip around the sun — and you just keep getting more wonderful! {ordinal} birthday and every single year has made the world a better place. Have the most amazing day!",
        // 3
        "Happy {ordinal} Birthday {n}! 🎂🌟 Wishing you a year full of beautiful surprises, big laughs, and moments worth remembering forever. Today is all about you — enjoy every single second!",
        // 4
        "{n}, I hope your {ordinal} birthday is filled with everything you love most. 🎈 You have this incredible way of making everyone around you feel special — today the world celebrates YOU!",
        // 5
        "Happy Birthday {n}! 🎂 You are one of those rare people who makes life genuinely richer just by being in it. Wishing you a day as warm, bright, and wonderful as you make every day for the rest of us!",
        // 6
        "Wishing the happiest {ordinal} birthday to {n}! 🥳 May this year bring you all the joy, peace, and adventure your heart desires. You have worked hard and you deserve every wonderful thing coming your way!",
        // 7
        "Happy Birthday {n}! 🎂✨ {ordinal} birthday and still the most amazing person I know. Hope your day is filled with cake, good company, and moments that make you smile for years to come!",
        // 8
        "{n}! It's your birthday and I hope it's absolutely spectacular! 🎉 Wishing you a year ahead that is full of growth, happiness, and all the beautiful things life has to offer. Love you loads!",
        // 9
        "Happy {ordinal} Birthday {n}! 🎂 I hope today feels like a warm hug from the universe. {years} years of you — and every single one has been a gift. Here's to making this year your best one yet!",
        // 10
        "Sending you the biggest birthday wishes {n}! 🎈 May your {ordinal} birthday mark the beginning of your most exciting chapter. The world is yours — go out there and make it beautiful!",
        // 11
        "Happy Birthday to the most genuine person I know — {n}! 🎂🌸 Hope today brings you as much happiness as you bring to everyone around you. You truly deserve all the love in the world!",
        // 12
        "{n}, on your {ordinal} birthday I want you to know how grateful I am to have you in my life. 🎉 You light up every room you walk into. Wishing you a year full of joy, laughter, and everything wonderful!",
        // 13
        "Happy {ordinal} Birthday {n}! 🎂💕 Another year of being absolutely you — and that is the greatest gift of all. Hope your special day is as extraordinary as the incredible person you are!",
        // 14
        "Today we celebrate {n} — and what a celebration it should be! 🎊 {ordinal} birthday of someone who makes the world undeniably better. Wishing you joy today and in every day that follows!",
        // 15
        "Happy Birthday {n}! 🎂⭐ {ordinal} trip around the sun and you shine brighter than ever. May this birthday be the start of a year filled with love, laughter, success, and every dream coming true!"
    )

    private val anniversaryWishes = listOf(
        // 1
        "Happy {ordinal} Anniversary {n}! 💑 Wishing you both endless love and happiness together. Your relationship is a beautiful reminder of what true commitment looks like. May your bond grow stronger with every passing year! 🥂",
        // 2
        "Congratulations on your {ordinal} anniversary {n}! 💕 What an incredible journey you have shared together! Every year of love, laughter, and growing together is something truly worth celebrating. Wishing you many more beautiful years ahead!",
        // 3
        "{ordinal} anniversary {n}! 💑 Your love story is genuinely an inspiration to everyone who witnesses it. Two people choosing each other every single day — there is nothing more beautiful than that. Here's to forever! 🌹",
        // 4
        "Happy {ordinal} Anniversary {n}! 💕 {years} years of love and your story just keeps getting more beautiful. The way you have built your life together is something truly special. Wishing you a day full of wonderful celebration!",
        // 5
        "Congratulations {n} on your {ordinal} anniversary! 🥂 Your relationship is proof that real love is patient, kind, and deeply worth fighting for. Wishing you both continued happiness, laughter, and a love that only deepens with time!",
        // 6
        "Happy {ordinal} Anniversary {n}! 💑 Every year together is a treasure and today we celebrate {years} of those treasures. Your love is a gift not just to each other but to everyone lucky enough to know you both!",
        // 7
        "Wishing you a beautiful {ordinal} anniversary {n}! 💕 The life you have built together is something to be genuinely proud of. May this day remind you of all the reasons you fell in love and fill your hearts with joy!",
        // 8
        "{n}, happy {ordinal} anniversary! 💑 {years} years of choosing each other — that takes courage, commitment, and a whole lot of love. Celebrating you both today and wishing you a future that is even brighter than your past!",
        // 9
        "Congratulations on {years} wonderful years together {n}! 🥂 Your love has weathered every storm and celebrated every joy. On your {ordinal} anniversary, wishing you both peace, happiness, and a love that lasts a lifetime!",
        // 10
        "Happy {ordinal} Anniversary {n}! 💕 What a milestone — and what a love story! Every chapter you have written together has been more beautiful than the last. Here's to many more years of the most wonderful adventure together!",
        // 11
        "{n}, {years} years together and your love story continues to inspire! 💑 On your {ordinal} anniversary, I wish you both a day full of joy and a future full of all the beautiful moments still to come. Congratulations!",
        // 12
        "Happy {ordinal} Anniversary {n}! 🌹 Real love grows richer and deeper with every passing year — and yours is a perfect example of that. Wishing you both a gorgeous celebration and many more extraordinary years together!",
        // 13
        "Congratulations on your {ordinal} anniversary {n}! 💕 {years} years of shared memories, adventures, challenges overcome, and joy multiplied. What you have built together is truly something to celebrate with the biggest of hearts!",
        // 14
        "Happy {ordinal} Anniversary {n}! 💑 Your relationship is a reminder that the best love stories are written not in grand gestures but in everyday moments of choosing each other. Wishing you both a day as beautiful as your love!",
        // 15
        "{n}, happy {ordinal} anniversary! 🥂 {years} years and still going strong — that is not just love, that is a true partnership. May your celebration today be as warm and beautiful as everything you have built together over the years!"
    )
}
