package com.example.wellwisher

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String = "",
    val cat: String,
    val date: String,
    val remindHour: Int = 9,
    val remindMin: Int = 0,
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

        val name = prefs.getString("user_name", "") ?: ""
        updateGreeting(name)

        loadContacts()

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = ContactAdapter(filtered,
            onWish = { openWishScreen(it) },
            onLongPress = { showEditDeleteDialog(it) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showAddDialog()
        }

        val search = findViewById<EditText>(R.id.etSearch)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { filterList(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        val tvGreeting = findViewById<TextView>(R.id.tvGreeting)
        tvGreeting.setOnClickListener { askUserName() }

        if (name.isEmpty()) askUserName()

        updateList()
        scheduleAllReminders()
    }

    private fun askUserName() {
        val input = EditText(this).apply {
            hint = "Your name e.g. Sandip"
            textSize = 16f
            setPadding(40, 20, 40, 20)
            setText(prefs.getString("user_name", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("What's your name?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    prefs.edit().putString("user_name", n).apply()
                    updateGreeting(n)
                }
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun updateGreeting(name: String) {
        val tv = findViewById<TextView>(R.id.tvGreeting)
        tv.text = if (name.isEmpty()) "Hi there 👋" else "Hi $name 👋"
    }

    private fun filterList(query: String) {
        filtered.clear()
        if (query.isEmpty()) filtered.addAll(contacts.sortedBy { daysUntil(it.date) })
        else filtered.addAll(contacts.filter { it.name.contains(query, true) }.sortedBy { daysUntil(it.date) })
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
        if (filtered.isEmpty()) { empty.visibility = View.VISIBLE; rv.visibility = View.GONE }
        else { empty.visibility = View.GONE; rv.visibility = View.VISIBLE }
    }

    private fun openWishScreen(contact: Contact) {
        saveContacts()
        val intent = Intent(this, WishActivity::class.java)
        intent.putExtra("contact_id", contact.id)
        startActivity(intent)
    }

    private fun showAddDialog(existing: Contact? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_add, null)
        val cats = listOf("birthday", "anniversary")
        val emojis = listOf("none", "few", "lots")

        var selectedDay = -1
        var selectedMonth = -1
        var selectedYear = -1
        var selectedHour = existing?.remindHour ?: 9
        var selectedMin = existing?.remindMin ?: 0

        val tvDateDisplay = view.findViewById<TextView>(R.id.tvDateDisplay)
        val tvTimeDisplay = view.findViewById<TextView>(R.id.tvTimeDisplay)
        val etDate = view.findViewById<EditText>(R.id.etDate)
        val etHour = view.findViewById<EditText>(R.id.etHour)
        val etMin = view.findViewById<EditText>(R.id.etMin)

        // Pre-fill if editing
        existing?.let {
            view.findViewById<EditText>(R.id.etName).setText(it.name)
            view.findViewById<EditText>(R.id.etPhone).setText(it.phone)
            view.findViewById<EditText>(R.id.etPersonal).setText(it.personalTouch)
            view.findViewById<Spinner>(R.id.spinnerCat).setSelection(cats.indexOf(it.cat).coerceAtLeast(0))
            view.findViewById<Spinner>(R.id.spinnerEmoji).setSelection(emojis.indexOf(it.emojiStyle).coerceAtLeast(0))
            etDate.setText(it.date)
            tvDateDisplay.text = formatDateDisplay(it.date)
            tvDateDisplay.setTextColor(0xFF1A1A2E.toInt())
            tvTimeDisplay.text = fmtTime(it.remindHour, it.remindMin)
            try {
                val parts = it.date.split("-")
                selectedYear = parts[0].toInt()
                selectedMonth = parts[1].toInt() - 1
                selectedDay = parts[2].toInt()
            } catch (e: Exception) {}
        }

        // Update time display
        tvTimeDisplay.text = fmtTime(selectedHour, selectedMin)

        // Date picker
        view.findViewById<View>(R.id.datePickerRow).setOnClickListener {
            val cal = Calendar.getInstance()
            val initYear = if (selectedYear > 0) selectedYear else cal.get(Calendar.YEAR)
            val initMonth = if (selectedMonth >= 0) selectedMonth else cal.get(Calendar.MONTH)
            val initDay = if (selectedDay > 0) selectedDay else cal.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, year, month, day ->
                selectedYear = year
                selectedMonth = month
                selectedDay = day
                val dateStr = "$year-${(month+1).toString().padStart(2,'0')}-${day.toString().padStart(2,'0')}"
                etDate.setText(dateStr)
                tvDateDisplay.text = formatDateDisplay(dateStr)
                tvDateDisplay.setTextColor(0xFF1A1A2E.toInt())
            }, initYear, initMonth, initDay).show()
        }

        // Time picker
        view.findViewById<View>(R.id.timePickerRow).setOnClickListener {
            TimePickerDialog(this, { _, hour, min ->
                selectedHour = hour
                selectedMin = min
                etHour.setText(hour.toString())
                etMin.setText(min.toString())
                tvTimeDisplay.text = fmtTime(hour, min)
            }, selectedHour, selectedMin, false).show()
        }

        val title = if (existing == null) "✨ Add Occasion" else "✏️ Edit Occasion"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setPositiveButton("Save. I'm Done") { _, _ ->
                val name = view.findViewById<EditText>(R.id.etName).text.toString().trim()
                val phone = view.findViewById<EditText>(R.id.etPhone).text.toString().trim()
                val date = etDate.text.toString().trim()
                val personal = view.findViewById<EditText>(R.id.etPersonal).text.toString().trim()
                val hour = etHour.text.toString().toIntOrNull() ?: 9
                val min = etMin.text.toString().toIntOrNull() ?: 0

                if (name.isEmpty() || date.isEmpty()) {
                    Toast.makeText(this, "Please fill in name and date", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val contact = Contact(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = name, phone = phone, date = date,
                    cat = cats[view.findViewById<Spinner>(R.id.spinnerCat).selectedItemPosition],
                    emojiStyle = emojis[view.findViewById<Spinner>(R.id.spinnerEmoji).selectedItemPosition],
                    remindHour = hour, remindMin = min,
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
                Toast.makeText(this, "✅ ${contact.name} saved! Reminder set yearly at ${fmtTime(hour, min)}", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Save & Add Another") { _, _ ->
                // same logic, then reopen
                val name = view.findViewById<EditText>(R.id.etName).text.toString().trim()
                val phone = view.findViewById<EditText>(R.id.etPhone).text.toString().trim()
                val date = etDate.text.toString().trim()
                val personal = view.findViewById<EditText>(R.id.etPersonal).text.toString().trim()
                val hour = etHour.text.toString().toIntOrNull() ?: 9
                val min = etMin.text.toString().toIntOrNull() ?: 0
                if (name.isNotEmpty() && date.isNotEmpty()) {
                    val contact = Contact(
                        name = name, phone = phone, date = date,
                        cat = cats[view.findViewById<Spinner>(R.id.spinnerCat).selectedItemPosition],
                        emojiStyle = emojis[view.findViewById<Spinner>(R.id.spinnerEmoji).selectedItemPosition],
                        remindHour = hour, remindMin = min, personalTouch = personal
                    )
                    contacts.add(contact)
                    saveContacts()
                    scheduleYearlyReminder(contact)
                    updateList()
                }
                showAddDialog()
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
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    fun getContact(id: String) = contacts.find { it.id == id }

    fun updateContactWishIndex(id: String, newIdx: Int) {
        val idx = contacts.indexOfFirst { it.id == id }
        if (idx >= 0) {
            contacts[idx] = contacts[idx].copy(wishIndex = newIdx)
            saveContacts()
        }
    }

    private fun scheduleAllReminders() {
        contacts.forEach { scheduleYearlyReminder(it) }
    }

    private fun scheduleYearlyReminder(contact: Contact) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, BirthdayReceiver::class.java).apply {
            putExtra("name", contact.name)
            putExtra("cat", contact.cat)
            putExtra("contact_id", contact.id)
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
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
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

    fun fmtTime(h: Int, m: Int): String {
        val ampm = if (h >= 12) "PM" else "AM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${m.toString().padStart(2, '0')} $ampm"
    }

    private fun formatDateDisplay(dateStr: String): String {
        return try {
            val parts = dateStr.split("-")
            val months = listOf("Jan","Feb","Mar","Apr","May","Jun",
                "Jul","Aug","Sep","Oct","Nov","Dec")
            "${months[parts[1].toInt()-1]} ${parts[2].toInt()}, ${parts[0]}"
        } catch (e: Exception) { dateStr }
    }

    private fun saveContacts() {
        val arr = JSONArray()
        contacts.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("phone", c.phone)
                put("cat", c.cat); put("date", c.date)
                put("remindHour", c.remindHour); put("remindMin", c.remindMin)
                put("emojiStyle", c.emojiStyle); put("wishIndex", c.wishIndex)
                put("personalTouch", c.personalTouch)
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
                cat = o.optString("cat", "birthday"),
                date = o.getString("date"),
                remindHour = o.optInt("remindHour", 9),
                remindMin = o.optInt("remindMin", 0),
                emojiStyle = o.optString("emojiStyle", "few"),
                wishIndex = o.optInt("wishIndex", 0),
                personalTouch = o.optString("personalTouch", "")
            ))
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("wellwisher", "WellWisher Reminders",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Birthday and anniversary reminders"
            enableVibration(true)
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
            val years = try {
                Calendar.getInstance().get(Calendar.YEAR) - c.date.split("-")[0].toInt()
            } catch (e: Exception) { 0 }

            val icon = if (c.cat == "anniversary") "💑" else "🎂"
            val catLabel = if (c.cat == "anniversary") "Anniversary" else "Birthday"
            val ageStr = if (years > 0 && c.cat == "birthday") " · Turning $years"
                else if (years > 0 && c.cat == "anniversary") " · ${years}th year"
                else ""

            holder.tvIcon.text = icon
            holder.tvName.text = c.name
            holder.tvOccasion.text = "$catLabel · ⏰ ${fmtTime(c.remindHour, c.remindMin)}"
            holder.tvDate.text = formatDateDisplay(c.date) + ageStr
            holder.tvDays.text = when (days) {
                0 -> "🎉 Today!"
                1 -> "Tomorrow!"
                else -> "in $days days"
            }
            holder.btnCreateWish.setOnClickListener { onWish(c) }
            holder.itemView.setOnLongClickListener { onLongPress(c); true }
        }

        private fun formatDateDisplay(dateStr: String): String {
            return try {
                val parts = dateStr.split("-")
                val months = listOf("Jan","Feb","Mar","Apr","May","Jun",
                    "Jul","Aug","Sep","Oct","Nov","Dec")
                "${months[parts[1].toInt()-1]} ${parts[2].toInt()}, ${parts[0]}"
            } catch (e: Exception) { dateStr }
        }
    }
}
