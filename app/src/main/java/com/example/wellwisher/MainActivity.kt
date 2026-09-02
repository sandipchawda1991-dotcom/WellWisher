package com.example.wellwisher

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val cat: String,
    val rel: String,
    val date: String,
    val remindHour: Int = 8,
    val remindMin: Int = 0,
    val msgLen: String = "long",
    val emojiStyle: String = "few",
    var wishIndex: Int = 0
)

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val contacts = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        loadContacts()
        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = ContactAdapter(contacts,
            onDelete = { deleteContact(it) },
            onEdit = { showEditDialog(it) },
            onWhatsApp = { openWhatsApp(it) },
            onShare = { shareWish(it) },
            onCopy = { copyWish(it) },
            onRefresh = { refreshWish(it) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        findViewById<Button>(R.id.btnAdd).setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add, null)
        val hourPicker = view.findViewById<NumberPicker>(R.id.hourPicker).apply {
            minValue = 0; maxValue = 23; value = 8
        }
        val minPicker = view.findViewById<NumberPicker>(R.id.minPicker).apply {
            minValue = 0; maxValue = 59; value = 0
        }
        AlertDialog.Builder(this)
            .setTitle("Add a person")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = view.findViewById<EditText>(R.id.etName).text.toString().trim()
                val phone = view.findViewById<EditText>(R.id.etPhone).text.toString().trim()
                val date = view.findViewById<EditText>(R.id.etDate).text.toString().trim()
                if (name.isEmpty() || phone.isEmpty() || date.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val cats = listOf("birthday","anniversary","work","graduation","newbaby","friendship","custom")
                val rels = listOf("friend","family","colleague","boss","partner","other")
                val lens = listOf("vshort","short","long","vlong")
                val emojis = listOf("none","few","more")
                val contact = Contact(
                    name = name, phone = phone, date = date,
                    cat = cats[view.findViewById<Spinner>(R.id.spinnerCat).selectedItemPosition],
                    rel = rels[view.findViewById<Spinner>(R.id.spinnerRel).selectedItemPosition],
                    msgLen = lens[view.findViewById<Spinner>(R.id.spinnerLen).selectedItemPosition],
                    emojiStyle = emojis[view.findViewById<Spinner>(R.id.spinnerEmoji).selectedItemPosition],
                    remindHour = hourPicker.value,
                    remindMin = minPicker.value
                )
                contacts.add(contact)
                saveContacts()
                scheduleReminder(contact)
                adapter.notifyItemInserted(contacts.size - 1)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(contact: Contact) {
        val view = layoutInflater.inflate(R.layout.dialog_add, null)
        view.findViewById<EditText>(R.id.etName).setText(contact.name)
        view.findViewById<EditText>(R.id.etPhone).setText(contact.phone)
        view.findViewById<EditText>(R.id.etDate).setText(contact.date)
        val hourPicker = view.findViewById<NumberPicker>(R.id.hourPicker).apply {
            minValue = 0; maxValue = 23; value = contact.remindHour
        }
        val minPicker = view.findViewById<NumberPicker>(R.id.minPicker).apply {
            minValue = 0; maxValue = 59; value = contact.remindMin
        }
        val cats = listOf("birthday","anniversary","work","graduation","newbaby","friendship","custom")
        val rels = listOf("friend","family","colleague","boss","partner","other")
        val lens = listOf("vshort","short","long","vlong")
        val emojis = listOf("none","few","more")
        view.findViewById<Spinner>(R.id.spinnerCat).setSelection(cats.indexOf(contact.cat).coerceAtLeast(0))
        view.findViewById<Spinner>(R.id.spinnerRel).setSelection(rels.indexOf(contact.rel).coerceAtLeast(0))
        view.findViewById<Spinner>(R.id.spinnerLen).setSelection(lens.indexOf(contact.msgLen).coerceAtLeast(0))
        view.findViewById<Spinner>(R.id.spinnerEmoji).setSelection(emojis.indexOf(contact.emojiStyle).coerceAtLeast(0))
        AlertDialog.Builder(this)
            .setTitle("Edit ${contact.name}")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val idx = contacts.indexOf(contact)
                val updated = contact.copy(
                    name = view.findViewById<EditText>(R.id.etName).text.toString().trim(),
                    phone = view.findViewById<EditText>(R.id.etPhone).text.toString().trim(),
                    date = view.findViewById<EditText>(R.id.etDate).text.toString().trim(),
                    cat = cats[view.findViewById<Spinner>(R.id.spinnerCat).selectedItemPosition],
                    rel = rels[view.findViewById<Spinner>(R.id.spinnerRel).selectedItemPosition],
                    msgLen = lens[view.findViewById<Spinner>(R.id.spinnerLen).selectedItemPosition],
                    emojiStyle = emojis[view.findViewById<Spinner>(R.id.spinnerEmoji).selectedItemPosition],
                    remindHour = hourPicker.value,
                    remindMin = minPicker.value
                )
                contacts[idx] = updated
                saveContacts()
                scheduleReminder(updated)
                adapter.notifyItemChanged(idx)
                Toast.makeText(this, "${updated.name} updated!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteContact(contact: Contact) {
        val idx = contacts.indexOf(contact)
        contacts.remove(contact)
        saveContacts()
        adapter.notifyItemRemoved(idx)
    }

    private fun openWhatsApp(contact: Contact) {
        val phone = contact.phone.replace(Regex("[^0-9+]"), "")
        val uri = Uri.parse("https://wa.me/$phone?text=${Uri.encode(getWish(contact))}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun shareWish(contact: Contact) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getWish(contact))
        }
        startActivity(Intent.createChooser(intent, "Share wish via"))
    }

    private fun copyWish(contact: Contact) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wish", getWish(contact)))
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
    }

    private fun refreshWish(contact: Contact) {
        val idx = contacts.indexOf(contact)
        val updated = contact.copy(wishIndex = (contact.wishIndex + 1) % getWishList(contact).size)
        contacts[idx] = updated
        saveContacts()
        adapter.notifyItemChanged(idx)
    }

    private fun scheduleReminder(contact: Contact) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BirthdayReceiver::class.java).apply {
            putExtra("name", contact.name)
            putExtra("cat", contact.cat)
        }
        val pi = PendingIntent.getBroadcast(
            this, contact.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val parts = contact.date.split("-")
            val now = Calendar.getInstance()
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, contact.remindHour)
                set(Calendar.MINUTE, contact.remindMin)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.YEAR, 1)
            }
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (e: Exception) { e.printStackTrace() }
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

    private fun yearsFrom(dateStr: String): Int {
        return try {
            Calendar.getInstance().get(Calendar.YEAR) - dateStr.split("-")[0].toInt()
        } catch (e: Exception) { 0 }
    }

    private fun daysUntil(dateStr: String): Int {
        return try {
            val parts = dateStr.split("-")
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val next = Calendar.getInstance().apply {
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now) || timeInMillis == now.timeInMillis) {
                    if (timeInMillis == now.timeInMillis) {
                        // it's today!
                    } else {
                        add(Calendar.YEAR, 1)
                    }
                }
            }
            ((next.timeInMillis - now.timeInMillis) / 86400000).toInt()
        } catch (e: Exception) { 999 }
    }

    private fun getWish(contact: Contact): String {
        val list = getWishList(contact)
        val idx = contact.wishIndex % list.size
        val n = contact.name.split(" ")[0]
        val y = yearsFrom(contact.date)
        var msg = list[idx]
            .replace("{n}", n)
            .replace("{years}", if (y > 0) y.toString() else "")
            .replace("{ordinal}", if (y > 0) ordinal(y) else "")
        val sentences = msg.split(". ").filter { it.isNotBlank() }
        msg = when (contact.msgLen) {
            "vshort" -> sentences.firstOrNull() ?: msg
            "short" -> sentences.take(2).joinToString(". ")
            "vlong" -> "$msg\n\nWishing you nothing but the best today and always!"
            else -> msg
        }
        if (contact.emojiStyle == "none") {
            msg = msg.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "").trim()
        }
        return msg
    }

    private fun getWishList(contact: Contact): List<String> {
        return when (contact.cat) {
            "anniversary" -> listOf(
                "Happy {ordinal} Anniversary {n}! 💑 Wishing you both endless love and happiness. May your bond grow stronger every year! 🥂",
                "Congratulations on your {ordinal} anniversary {n}! 💑 What an incredible journey of love. Wishing you many more years of laughter and happiness together! ❤️",
                "{ordinal} anniversary {n}! 💑 Your love story is truly an inspiration to everyone around you. Wishing you a lifetime of cherished moments! 🌹",
                "Happy {ordinal} Anniversary {n}! 💑 {ordinal} year of choosing each other — that is one of the most beautiful things in the world. Here's to forever! ❤️",
                "Congratulations {n} on your {ordinal} anniversary! 💑 Your love continues to shine as an example for everyone. Wishing you a wonderful celebration! 🥂"
            )
            "work" -> listOf(
                "Happy {ordinal} Work Anniversary {n}! 💼 Your dedication and hard work are truly something to celebrate. Here's to many more years of success! 🎉",
                "Congratulations on your {ordinal} work anniversary {n}! 💼 You have shown up and made a real difference every single day. So proud of everything you have achieved! 🌟",
                "{ordinal} work anniversary {n}! 💼 Another year of being absolutely brilliant at what you do. Your drive inspires everyone around you. Here's to even bigger things ahead! 🚀",
                "Happy {ordinal} Work Anniversary {n}! 💼 Not everyone turns their passion into purpose the way you do. Congratulations on this milestone! 🏆",
                "Congratulations {n}! 💼 {ordinal} work anniversary — and you are still the person everyone wants on their team. Your energy makes all the difference! 🌟"
            )
            "graduation" -> listOf(
                "Congratulations {n}! 🎓 You did it — all those late nights and hard work led you exactly here. This is just the beginning of something incredible! 🌟",
                "{n}, graduate! 🎓 So ridiculously proud of you. Your dedication and perseverance brought you to this incredible milestone. The future is yours! 🚀",
                "Congratulations on your graduation {n}! 🎓 You have worked for this, you have earned this, and you absolutely deserve this. Now go make your mark! 🌍",
                "You graduated {n}! 🎓 Your dedication, resilience, and brilliant mind led you here. The world better be ready because here you come! 💪",
                "Congratulations {n}! 🎓 This is proof of what you are made of — strength, determination, and brilliance. The real adventure begins now! ✨"
            )
            "friendship" -> listOf(
                "Happy Friendship Day {n}! 🤝 Thank you for being one of those rare people who makes life genuinely better. Your friendship is one of my greatest treasures! ❤️",
                "{n}! Happy Friendship Day! 🤝 There are friends and then there are people like you — who show up, listen, and make you laugh when you need it most! 💕",
                "Happy Friendship Day {n}! 🤝 You have a way of making every moment feel warmer. Thank you for being my constant. Here's to our friendship forever! ❤️",
                "{n}, on this Friendship Day I want you to know how much your friendship means to me. 🤝 You have shaped who I am. Thank you for everything! 💕",
                "Happy Friendship Day {n}! 🤝 Life with you is richer, funnier, and so much more meaningful. Thank you for every conversation and every moment of laughter! ❤️"
            )
            "newbaby" -> listOf(
                "Happy Birthday to the little star! 👶🎂 Sending so much love to {n} on this special day! May every birthday be more magical than the last! 🎉",
                "It's {n}'s birthday! 👶🎂 Wishing your precious little one the most wonderful day filled with giggles, cuddles, and all the love in the world! ❤️",
                "Happy Birthday {n}! 👶🎂 Another year of growing and bringing so much joy to everyone around. Sending all the birthday love and hugs! 🎊",
                "{ordinal} birthday for the most adorable little one! Happy Birthday {n}! 👶 Wishing your precious child a day full of laughter and love! 💕",
                "Happy Birthday to little {n}! 👶🎂 Wishing your little star a day full of fun, laughter, and the sweetest birthday memories! 🎉"
            )
            "custom" -> listOf(
                "Wishing you a very special day {n}! ✨ Hope this occasion brings you immense joy. Thinking of you and sending all the best wishes your way! 🎉",
                "Happy special day {n}! ✨ Hope today is everything you hoped for and more. Sending you lots of love and warmest wishes! 💕",
                "Wishing you all the best {n}! ✨ Today is your special day and you deserve to celebrate it fully. Hope it is absolutely wonderful! 🎊",
                "{n}! Wishing you a day as special as you are. ✨ However you celebrate, I hope it is full of joy and everything that makes you smile! ❤️",
                "Happy special occasion {n}! ✨ Days like these are worth celebrating with everything you have. Hope yours is full of warmth and beautiful memories! 💫"
            )
            else -> when (contact.rel) {
                "partner" -> listOf(
                    "Happy {ordinal} Birthday my love! 🎂❤️ Every day with you is a gift but today we celebrate you! Wishing you all the happiness in the world. Love you to the moon and back! 💕",
                    "Happy Birthday my darling! 🎂💕 You make every day brighter just by being in it. Today is your day and I want it to be as magical as you are. I love you so much! ❤️",
                    "To the most amazing person in my life — Happy {ordinal} Birthday! 🎂 Thank you for your love, your laughter, and your beautiful soul. Celebrating you today and always! 💕",
                    "Happy {ordinal} Birthday to the love of my life! 🎂❤️ I fall more in love with you every single day. You are my everything. I love you beyond measure!",
                    "My love, Happy {ordinal} Birthday! 🎂💕 Every year with you makes my life richer and more beautiful. Thank you for being my greatest blessing! ❤️"
                )
                "family" -> listOf(
                    "Happy {ordinal} Birthday {n}! 🎂❤️ So grateful to have you in my life. Wishing you a day full of love, happiness, and all your favourite things!",
                    "Wishing my dear {n} the happiest {ordinal} birthday! 🎂❤️ You fill our lives with so much love and warmth. Today is all about you — enjoy every moment!",
                    "Happy Birthday {n}! 🎂 You are such a blessing to our family. May this day bring you all the joy you bring us every single day. Love you always! ❤️",
                    "Happy {ordinal} Birthday {n}! 🎂❤️ Watching you grow fills our hearts with so much pride and joy. Wishing you the most wonderful day!",
                    "{n}, may your {ordinal} birthday be as bright as the light you bring into our lives. Happy Birthday! We love you so much. 🎂❤️"
                )
                "colleague" -> listOf(
                    "Happy {ordinal} Birthday {n}! 🎂 Wishing you a fantastic day. It's a pleasure working with you. Hope your day is as amazing as you are! 🎉",
                    "Many happy returns {n}! 🎂🎉 Hope your {ordinal} birthday is filled with cake, good company, and zero meetings! Wishing you a brilliant year ahead!",
                    "Happy Birthday {n}! 🎂 The office is a better place because of you. Hope your special day is everything you wished for. Enjoy your celebrations! 🎊",
                    "Happy {ordinal} Birthday {n}! 🎂 Your energy and talent inspire everyone. Hope today brings you all the joy you so richly deserve!",
                    "{n}! Happy Birthday! 🎂🎉 Hope it's a day full of great food and zero work stress. Wishing you a fantastic {ordinal} birthday!"
                )
                "boss" -> listOf(
                    "Wishing you a very Happy {ordinal} Birthday {n}! 🎂 Thank you for your guidance and leadership. Hope you have a wonderful day and great year ahead!",
                    "Happy {ordinal} Birthday {n}! 🎂 Your leadership and vision inspire all of us every day. Hope today is as exceptional as you are!",
                    "Many happy returns {n}! 🎂 It's a privilege to work under your guidance. Wishing you a day full of joy and a year full of success!",
                    "Happy Birthday {n}! 🎂 Celebrating a truly exceptional leader. Thank you for everything you do for this team!",
                    "Warmest birthday wishes {n}! 🎂 {ordinal} years of wisdom and excellence. Thank you for your mentorship. May this be your most rewarding year yet!"
                )
                else -> listOf(
                    "Hey {n}! 🎂🎉 Wishing you the most amazing {ordinal} birthday ever! Hope today is as incredible as you are. You deserve only the best!",
                    "Happy {ordinal} Birthday {n}! 🎂 Another year older, wiser, and more awesome! Hope your day is packed with fun, laughter, and everything you love!",
                    "{n}! It's your {ordinal} birthday! 🎂🌟 Hope today is as incredible as you are. Wishing you a year full of adventures and dreams coming true!",
                    "Happy Birthday to one of the most genuine people I know — {n}! 🎂 {ordinal} years of being absolutely awesome. Hope today brings you so much happiness!",
                    "Wishing the happiest {ordinal} birthday to {n}! 🎂 You have this incredible way of making everyone feel special — today it is your turn! 🎉"
                )
            }
        }
    }

    private fun saveContacts() {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("phone", c.phone)
                put("cat", c.cat); put("rel", c.rel); put("date", c.date)
                put("remindHour", c.remindHour); put("remindMin", c.remindMin)
                put("msgLen", c.msgLen); put("emojiStyle", c.emojiStyle)
                put("wishIndex", c.wishIndex)
            })
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    private fun loadContacts() {
        contacts.clear()
        val arr = JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            contacts.add(Contact(
                id = o.optString("id", UUID.randomUUID().toString()),
                name = o.getString("name"), phone = o.getString("phone"),
                cat = o.getString("cat"), rel = o.getString("rel"),
                date = o.getString("date"),
                remindHour = o.optInt("remindHour", 8),
                remindMin = o.optInt("remindMin", 0),
                msgLen = o.optString("msgLen", "long"),
                emojiStyle = o.optString("emojiStyle", "few"),
                wishIndex = o.optInt("wishIndex", 0)
            ))
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("wellwisher", "Birthday Reminders",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Birthday and anniversary reminders"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    inner class ContactAdapter(
        private val list: MutableList<Contact>,
        private val onDelete: (Contact) -> Unit,
        private val onEdit: (Contact) -> Unit,
        private val onWhatsApp: (Contact) -> Unit,
        private val onShare: (Contact) -> Unit,
        private val onCopy: (Contact) -> Unit,
        private val onRefresh: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvAvatar: TextView = v.findViewById(R.id.tvAvatar)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvMeta: TextView = v.findViewById(R.id.tvMeta)
            val tvDays: TextView = v.findViewById(R.id.tvDays)
            val tvWish: TextView = v.findViewById(R.id.tvWish)
            val btnEdit: ImageButton = v.findViewById(R.id.btnEdit)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
            val btnWhatsApp: Button = v.findViewById(R.id.btnWhatsApp)
            val btnShare: Button = v.findViewById(R.id.btnShare)
            val btnCopy: Button = v.findViewById(R.id.btnCopy)
            val btnRefresh: Button = v.findViewById(R.id.btnRefresh)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = list[position]
            holder.tvAvatar.text = c.name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            holder.tvName.text = c.name
            holder.tvMeta.text = "${c.date} · ${c.cat} · ${c.rel}"
            val days = daysUntil(c.date)
            holder.tvDays.text = if (days == 0) "🎉 Today!" else "📅 $days days away"
            holder.tvWish.text = "\"${getWish(c).take(150)}\""
            holder.btnEdit.setOnClickListener { onEdit(c) }
            holder.btnDelete.setOnClickListener { onDelete(c) }
            holder.btnWhatsApp.setOnClickListener { onWhatsApp(c) }
            holder.btnShare.setOnClickListener { onShare(c) }
            holder.btnCopy.setOnClickListener { onCopy(c) }
            holder.btnRefresh.setOnClickListener { onRefresh(c) }
        }
    }
}
