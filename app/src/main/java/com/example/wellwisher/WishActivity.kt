package com.example.wellwisher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class WishActivity : AppCompatActivity() {

    private lateinit var contact: Contact
    private var currentWish = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wish)

        val id = intent.getStringExtra("contact_id") ?: return finish()
        val mainActivity = (applicationContext as? MainActivity)
        contact = (application as? Any).let {
            val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
            val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
            var found: Contact? = null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("id") == id) {
                    found = Contact(
                        id = o.optString("id"), name = o.getString("name"),
                        phone = o.optString("phone", ""), cat = o.getString("cat"),
                        rel = o.getString("rel"), date = o.getString("date"),
                        remindHour = o.optInt("remindHour", 9),
                        remindMin = o.optInt("remindMin", 0),
                        msgLen = o.optString("msgLen", "long"),
                        emojiStyle = o.optString("emojiStyle", "few"),
                        wishIndex = o.optInt("wishIndex", 0),
                        personalTouch = o.optString("personalTouch", "")
                    )
                }
            }
            found
        } ?: return finish()

        val icon = when(contact.cat) {
            "anniversary" -> "💑"; "work" -> "💼"; "graduation" -> "🎓"
            "newbaby" -> "👶"; "friendship" -> "🤝"; "custom" -> "✨"; else -> "🎂"
        }
        val catLabel = when(contact.cat) {
            "anniversary" -> "Anniversary"; "work" -> "Work Anniversary"
            "graduation" -> "Graduation"; "newbaby" -> "Baby Birthday"
            "friendship" -> "Friendship Day"; "custom" -> "Special Day"; else -> "Birthday"
        }
        val relLabel = when(contact.rel) {
            "friend" -> "A Friend"; "family" -> "Family"; "colleague" -> "Colleague"
            "boss" -> "Boss"; "partner" -> "Partner"; else -> "Other"
        }

        findViewById<TextView>(R.id.tvWishIcon).text = icon
        findViewById<TextView>(R.id.tvWishName).text = contact.name
        findViewById<TextView>(R.id.tvWishMeta).text = "$catLabel · $relLabel"

        generateAndShowWish()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener { 
            contact = contact.copy(wishIndex = contact.wishIndex + 1)
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
            Toast.makeText(this, "You can now edit the message directly", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateAndShowWish() {
        currentWish = buildWish(contact)
        findViewById<TextView>(R.id.tvWishMessage).text = currentWish
    }

    private fun saveWishIndex() {
        val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == contact.id) {
                o.put("wishIndex", contact.wishIndex)
                break
            }
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    private fun openWhatsApp() {
        val phone = contact.phone.replace(Regex("[^0-9+]"), "")
        if (phone.isEmpty()) {
            shareWish(); return
        }
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(currentWish)}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun copyWish() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wish", currentWish))
        Toast.makeText(this, "✅ Copied!", Toast.LENGTH_SHORT).show()
    }

    private fun shareWish() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentWish)
        }
        startActivity(Intent.createChooser(intent, "Share wish via"))
    }

    private fun ordinal(n: Int): String {
        val s = when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
        return "$n$s"
    }

    private fun buildWish(c: Contact): String {
        val n = c.name.split(" ")[0]
        val y = try { Calendar.getInstance().get(Calendar.YEAR) - c.date.split("-")[0].toInt() } catch (e: Exception) { 0 }
        val list = getWishList(c)
        val idx = c.wishIndex % list.size
        var msg = list[idx]
            .replace("{n}", n)
            .replace("{ordinal}", if (y > 0) ordinal(y) else "")

        if (c.personalTouch.isNotEmpty()) {
            msg += " ${c.personalTouch}!"
        }

        if (c.emojiStyle == "none") {
            msg = msg.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "").trim()
        }
        return msg
    }

    private fun getWishList(c: Contact): List<String> {
        return when (c.cat) {
            "anniversary" -> listOf(
                "Happy {ordinal} Anniversary {n}! 💑 Wishing you both endless love and happiness. May your bond grow stronger every year! 🥂",
                "Congratulations on your {ordinal} anniversary {n}! 💑 What an incredible journey of love. Wishing you many more years of laughter and deep happiness! ❤️",
                "{ordinal} anniversary {n}! 💑 Your love story is truly an inspiration. Wishing you a lifetime of cherished moments! 🌹",
                "Happy {ordinal} Anniversary {n}! 💑 Choosing each other every single day — that is the most beautiful thing. Here's to forever! ❤️",
                "Congratulations {n} on your {ordinal} anniversary! 💑 Your love shines as an example for everyone. Wishing you a wonderful celebration! 🥂"
            )
            "work" -> listOf(
                "Happy {ordinal} Work Anniversary {n}! 💼 Your dedication and hard work truly deserve to be celebrated. Here's to many more years of success! 🎉",
                "Congratulations on your {ordinal} work anniversary {n}! 💼 You have made a real difference every single day. So proud of everything you have achieved! 🌟",
                "{ordinal} work anniversary {n}! 💼 Another year of being absolutely brilliant at what you do. Your drive inspires everyone! 🚀",
                "Happy {ordinal} Work Anniversary {n}! 💼 You turn passion into purpose every single day. Congratulations on this milestone! 🏆",
                "Congratulations {n}! 💼 {ordinal} work anniversary — still the person everyone wants on their team. Your energy makes all the difference! 🌟"
            )
            "graduation" -> listOf(
                "Congratulations {n}! 🎓 You did it — all those late nights led you exactly here. This is just the beginning of something incredible! 🌟",
                "{n}, you graduated! 🎓 So ridiculously proud of you. Your dedication brought you to this milestone. The future is yours! 🚀",
                "Congratulations on your graduation {n}! 🎓 You worked for this, earned this, and absolutely deserve this. Now go make your mark! 🌍",
                "You graduated {n}! 🎓 Your resilience and brilliant mind led you here. The world better be ready! 💪",
                "Congratulations {n}! 🎓 This is proof of your strength and brilliance. The real adventure begins now! ✨"
            )
            "friendship" -> listOf(
                "Happy Friendship Day {n}! 🤝 Thank you for being one of those rare people who makes life genuinely better. Your friendship is my greatest treasure! ❤️",
                "{n}! Happy Friendship Day! 🤝 There are friends and then there are people like you — who show up and make you laugh when you need it most! 💕",
                "Happy Friendship Day {n}! 🤝 You make every moment feel warmer. Thank you for being my constant. Here's to our friendship forever! ❤️",
                "{n}, on this Friendship Day I want you to know how much your friendship means to me. 🤝 Thank you for everything! 💕",
                "Happy Friendship Day {n}! 🤝 Life with you is richer and more meaningful. Thank you for every laugh and every beautiful memory! ❤️"
            )
            "newbaby" -> listOf(
                "Happy Birthday to the little star {n}! 👶🎂 Sending so much love! May every birthday be more magical than the last! 🎉",
                "It's {n}'s {ordinal} birthday! 👶🎂 Wishing your little one a day full of giggles, cuddles, and all the love in the world! ❤️",
                "Happy {ordinal} Birthday {n}! 👶🎂 Another year of growing and bringing joy to everyone around. Sending all the birthday love! 🎊",
                "{ordinal} birthday for the most adorable little one! Happy Birthday {n}! 👶 A day full of laughter and love! 💕",
                "Happy Birthday to little {n}! 👶🎂 Wishing your star a day full of fun and the sweetest birthday memories! 🎉"
            )
            "custom" -> listOf(
                "Wishing you a very special day {n}! ✨ Hope this occasion brings you immense joy! 🎉",
                "Happy special day {n}! ✨ Hope today is everything you hoped for and more. Sending you lots of love! 💕",
                "Wishing you all the best {n}! ✨ Today is your special day and you deserve to celebrate it fully! 🎊",
                "{n}! Wishing you a day as special as you are. ✨ I hope it is full of joy and everything that makes you smile! ❤️",
                "Happy special occasion {n}! ✨ May this day be full of warmth and beautiful memories! 💫"
            )
            else -> when (c.rel) {
                "partner" -> listOf(
                    "Happy {ordinal} Birthday my love! 🎂❤️ Every day with you is a gift but today we celebrate you! Wishing you all the happiness in the world. Love you to the moon and back! 💕",
                    "Happy Birthday my darling! 🎂💕 You make every day brighter. Today is your day — I want it to be as magical as you are. I love you so much! ❤️",
                    "To the most amazing person in my life — Happy {ordinal} Birthday! 🎂 Thank you for your love and your beautiful soul. Here's to celebrating you today and always! 💕",
                    "Happy {ordinal} Birthday to the love of my life! 🎂❤️ I fall more in love with you every single day. You are my everything! 💕",
                    "My love, Happy {ordinal} Birthday! 🎂💕 Every year with you makes my life richer and more beautiful. Thank you for being my greatest blessing! ❤️"
                )
                "family" -> listOf(
                    "Happy {ordinal} Birthday {n}! 🎂❤️ So grateful to have you in my life. Wishing you a day full of love and all your favourite things!",
                    "Wishing my dear {n} the happiest {ordinal} birthday! 🎂❤️ You fill our lives with so much love. Today is all about you — enjoy every moment!",
                    "Happy Birthday {n}! 🎂 You are such a blessing to our family. May this day bring you all the joy you bring us. Love you always! ❤️",
                    "Happy {ordinal} Birthday {n}! 🎂❤️ Watching you grow fills our hearts with so much pride. Wishing you the most wonderful day!",
                    "{n}, may your {ordinal} birthday be as bright as the light you bring into our lives. Happy Birthday! We love you so much! 🎂❤️"
                )
                "colleague" -> listOf(
                    "Happy {ordinal} Birthday {n}! 🎂 Wishing you a fantastic day. It's a pleasure working with you! 🎉",
                    "Many happy returns {n}! 🎂🎉 Hope your {ordinal} birthday is filled with cake and good company. Wishing you a brilliant year!",
                    "Happy Birthday {n}! 🎂 The office is better because of you. Hope your special day is everything you wished for! 🎊",
                    "Happy {ordinal} Birthday {n}! 🎂 Your energy and talent inspire everyone. Hope today brings you all the joy you deserve!",
                    "{n}! Happy Birthday! 🎂🎉 Hope it's a day full of great food and zero work stress. Happy {ordinal} birthday!"
                )
                "boss" -> listOf(
                    "Wishing you a very Happy {ordinal} Birthday {n}! 🎂 Thank you for your guidance. Hope you have a wonderful day!",
                    "Happy {ordinal} Birthday {n}! 🎂 Your leadership inspires all of us. Hope today is as exceptional as you are!",
                    "Many happy returns {n}! 🎂 It's a privilege to work under your guidance. Wishing you a day full of joy!",
                    "Happy Birthday {n}! 🎂 Celebrating a truly exceptional leader. Thank you for everything!",
                    "Warmest birthday wishes {n}! 🎂 Thank you for your mentorship. May this be your most rewarding year yet!"
                )
                else -> listOf(
                    "Hey {n}! 🎂🎉 Wishing you the most amazing {ordinal} birthday ever! Hope today is as incredible as you are!",
                    "Happy {ordinal} Birthday {n}! 🎂 Another year older, wiser, and more awesome! Hope your day is packed with everything you love!",
                    "{n}! It's your {ordinal} birthday! 🎂🌟 Hope today is incredible. Wishing you a year full of adventures and dreams coming true!",
                    "Happy Birthday {n}! 🎂 {ordinal} year of being absolutely awesome. Hope today brings you so much happiness!",
                    "Wishing the happiest {ordinal} birthday to {n}! 🎂 You have this incredible way of making everyone feel special — today it is your turn! 🎉"
                )
            }
        }
    }
}
