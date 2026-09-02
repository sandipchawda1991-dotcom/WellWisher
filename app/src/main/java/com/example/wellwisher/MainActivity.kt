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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val cat: String,
    val rel: String,
    val date: String,
    val remindHour: Int = 9,
    val remindMin: Int = 0,
    val msgLen: String = "long",
    val emojiStyle: String = "few",
    val personalTouch: String = "",
    var wishIndex: Int = 0
)

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private val contacts = mutableListOf<Contact>()
    private val filtered = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        loadContacts()

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = ContactAdapter(filtered,
            onWish = { openWishScreen(it) },
            onLongPress = { showEditDeleteDialog(it) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showAddDialog() }

        val search = findViewById<EditText>(R.id.etSearch)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { filterList(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        updateList()
    }

    private fun filterList(query: String) {
        filtered.clear()
        if (query.isEmpty()) filtered.addAll(contacts)
        else filtered.addAll(contacts.filter { it.name.contains(query, true) })
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateList() {
        filtered.clear()
        filtered.addAll(contacts.sortedBy { daysUntil(it.date) })
        adapter.notifyDataSetChanged()
        updateEmptyState()

        val todayCount = contacts.count { daysUntil(it.date) == 0 }
        val title = if (todayCount > 0) "Today's Celebrations 🎉" else "Upcoming Occasions 📅"
        findViewById<TextView>(R.id.tvSectionTitle).text = title
    }

    private fun updateEmptyState() {
        val empty = findViewById<LinearLayout>(R.id.emptyState)
        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        if (filtered.isEmpty()) {
            empty.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            empty.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    private fun openWishScreen(contact: Contact) {
        val intent = Intent(this, WishActivity::class.java)
        intent.putExtra("contact_id", contact.id)
        startActivity(intent)
    }

    private fun showAddDialog(existing: Contact? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_add, null)
        val hourPicker = view.findViewById<NumberPicker>(R.id.hourPicker).apply {
            minValue = 0; maxValue = 23; value = existing?.remindHour ?: 9
        }
        val minPicker = view.findViewById<NumberPicker>(R.id.minPicker).apply {
            minValue = 0; maxValue = 59; value = existing?.remindMin ?: 0
        }
        val cats = listOf("birthday","anniversary","work","graduation","newbaby","friendship","custom")
        val rels = listOf("friend","family","colleague","boss","partner","other")
        val lens = listOf("warm","funny","formal","heartfelt")
        val emojis = listOf("none","few","some","lots")

        existing?.let {
            view.findViewById<EditText>(R.id.etName).setText(it.name)
            view.findViewById<EditText>(R.id.etPhone).setText(it.phone)
            view.findViewById<EditText>(R.id.etDate).setText(it.date)
            view.findViewById<EditText>(R.id.etPersonal).setText(it.personalTouch)
            view.findViewById<Spinner>(R.id.spinnerCat).setSelection(cats.indexOf(it.cat).coerceAtLeast(0))
            view.findViewById<Spinner>(R.id.spinnerRel).setSelection(rels.indexOf(it.rel).coerceAtLeast(0))
            view.findViewById<Spinner>(R.id.spinnerLen).setSelection(lens.indexOf(it.msgLen).coerceAtLeast(0))
            view.findViewById<Spinner>(R.id.spinnerEmoji).setSelection(emojis.indexOf(it.emojiStyle).coerceAtLeast(0))
        }

        val title = if (existing == null) "✨ Add Occasion" else "✏️ Edit Occasion"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(if (existing == null) "Save. I'm Done" else "Save") { _, _ ->
                val name = view.findViewById<EditText>(R.id.etName).text.toString().trim()
                val phone = view.findViewById<EditText>(R.id.etPhone).text.toString().trim()
                val date = view.findViewById<EditText>(R.id.etDate).text.toString().trim()
                val personal = view.findViewById<EditText>(R.id.etPersonal).text.toString().trim()
                if (name.isEmpty() || date.isEmpty()) {
                    Toast.makeText(this, "Please fill name and date", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val contact = Contact(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name, phone = phone, date = date,
                    cat = cats[view.findViewById<Spinner>(R.id.spinnerCat).selectedItemPosition],
                    rel = rels[view.findViewById<Spinner>(R.id.spinnerRel).selectedItemPosition],
                    msgLen = lens[view.findViewById<Spinner>(R.id.spinnerLen).selectedItemPosition],
                    emojiStyle = emojis[view.findViewById<Spinner>(R.id.spinnerEmoji).selectedItemPosition],
                    remindHour = hourPicker.value,
                    remindMin = minPicker.value,
                    personalTouch = personal,
                    wishIndex = existing?.wishIndex ?: 0
                )
                if (existing != null) {
                    val idx = contacts.indexOfFirst { it.id == existing.id }
                    if (idx >= 0) contacts[idx] = contact
                } else {
                    contacts.add(contact)
                }
                saveContacts()
                scheduleYearlyReminder(contact)
                updateList()
                Toast.makeText(this, "✅ Saved! Yearly reminder set for ${contact.name}", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDeleteDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(contact.name)
            .setItems(arrayOf("✏️ Edit", "🗑️ Delete")) { _, which ->
                when (which) {
                    0 -> showAddDialog(contact)
                    1 -> confirmDelete(contact)
                }
            }.show()
    }

    private fun confirmDelete(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${contact.name}?")
            .setMessage("Their yearly reminder will also be cancelled.")
            .setPositiveButton("Delete") { _, _ ->
                cancelReminder(contact)
                contacts.removeAll { it.id == contact.id }
                saveContacts()
                updateList()
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun getContact(id: String) = contacts.find { it.id == id }

    fun updateContact(contact: Contact) {
        val idx = contacts.indexOfFirst { it.id == contact.id }
        if (idx >= 0) { contacts[idx] = contact; saveContacts(); updateList() }
    }

    private fun scheduleYearlyReminder(contact: Contact) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BirthdayReceiver::class.java).apply {
            putExtra("name", contact.name); putExtra("cat", contact.cat)
        }
        val pi = PendingIntent.getBroadcast(this, contact.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            val parts = contact.date.split("-")
            val now = Calendar.getInstance()
            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, contact.remindHour)
                set(Calendar.MINUTE, contact.remindMin)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.YEAR, 1)
            }
            am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                365L * 24 * 60 * 60 * 1000, pi)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun cancelReminder(contact: Contact) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(this, contact.id.hashCode(),
            Intent(this, BirthdayReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }

    fun daysUntil(dateStr: String): Int {
        return try {
            val parts = dateStr.split("-")
            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val next = Calendar.getInstance().apply {
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.YEAR, 1)
            }
            ((next.timeInMillis - now.timeInMillis) / 86400000).toInt()
        } catch (e: Exception) { 999 }
    }

    private fun saveContacts() {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("phone", c.phone)
                put("cat", c.cat); put("rel", c.rel); put("date", c.date)
                put("remindHour", c.remindHour); put("remindMin", c.remindMin)
                put("msgLen", c.msgLen); put("emojiStyle", c.emojiStyle)
                put("wishIndex", c.wishIndex); put("personalTouch", c.personalTouch)
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
                name = o.getString("name"),
                phone = o.optString("phone", ""),
                cat = o.getString("cat"), rel = o.getString("rel"),
                date = o.getString("date"),
                remindHour = o.optInt("remindHour", 9),
                remindMin = o.optInt("remindMin", 0),
                msgLen = o.optString("msgLen", "long"),
                emojiStyle = o.optString("emojiStyle", "few"),
                wishIndex = o.optInt("wishIndex", 0),
                personalTouch = o.optString("personalTouch", "")
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
        private val list: List<Contact>,
        private val onWish: (Contact) -> Unit,
        private val onLongPress: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvIcon: TextView = v.findViewById(R.id.tvIcon)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvOccasion: TextView = v.findViewById(R.id.tvOccasion)
            val tvRelationship: TextView = v.findViewById(R.id.tvRelationship)
            val tvDate: TextView = v.findViewById(R.id.tvDate)
            val tvDays: TextView = v.findViewById(R.id.tvDays)
            val btnCreateWish: Button = v.findViewById(R.id.btnCreateWish)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

        override fun getItemCount() = list.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = list[position]
            val days = daysUntil(c.date)
            val years = try { Calendar.getInstance().get(Calendar.YEAR) - c.date.split("-")[0].toInt() } catch (e: Exception) { 0 }

            val icon = when(c.cat) {
                "anniversary" -> "💑"; "work" -> "💼"; "graduation" -> "🎓"
                "newbaby" -> "👶"; "friendship" -> "🤝"; "custom" -> "✨"; else -> "🎂"
            }
            val catLabel = when(c.cat) {
                "anniversary" -> "Anniversary"; "work" -> "Work Anniversary"
                "graduation" -> "Graduation"; "newbaby" -> "Baby Birthday"
                "friendship" -> "Friendship Day"; "custom" -> "Special Day"; else -> "Birthday"
            }
            val relLabel = when(c.rel) {
                "friend" -> "A Friend"; "family" -> "Family"; "colleague" -> "Colleague"
                "boss" -> "Boss"; "partner" -> "Partner"; else -> "Other"
            }

            holder.tvIcon.text = icon
            holder.tvName.text = c.name
            holder.tvOccasion.text = catLabel
            holder.tvRelationship.text = relLabel
            val ageStr = if (years > 0) " · Turning $years" else ""
            holder.tvDate.text = c.date + ageStr
            holder.tvDays.text = if (days == 0) "🎉 Today!" else "in $days days"
            holder.btnCreateWish.setOnClickListener { onWish(c) }
            holder.itemView.setOnLongClickListener { onLongPress(c); true }
        }
    }
}
