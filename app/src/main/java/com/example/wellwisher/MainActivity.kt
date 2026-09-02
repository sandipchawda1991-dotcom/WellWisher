package com.example.wellwisher

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createNotificationChannel()
        prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)

        val name = prefs.getString("user_name", "") ?: ""
        updateGreeting(name)
        loadContacts()
        setupAdapter()
        setupFilters()
        setupCalendarStrip()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivityForResult(Intent(this, AddOccasionActivity::class.java), 100)
        }

        findViewById<TextView>(R.id.tvGreeting).setOnClickListener { askUserName() }
        if (name.isEmpty()) askUserName()

        updateList()
        scheduleAllReminders()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            loadContacts()
            updateList()
            setupCalendarStrip()
        }
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
        updateList()
        setupCalendarStrip()
    }

    private fun setupAdapter() {
        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = ContactAdapter(filtered,
            onWish = { openWishScreen(it) },
            onLongPress = { showEditDeleteDialog(it) }
        )
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    private fun setupFilters() {
        val buttons = mapOf(
            R.id.btnFilterAll to "all",
            R.id.btnFilterWeek to "week",
            R.id.btnFilterMonth to "month",
            R.id.btnFilterBirthday to "birthday",
            R.id.btnFilterAnniversary to "anniversary"
        )
        buttons.forEach { (id, filter) ->
            findViewById<Button>(id).setOnClickListener {
                currentFilter = filter
                updateFilterButtons(id)
                updateList()
            }
        }
    }

    private fun updateFilterButtons(activeId: Int) {
        val all = listOf(R.id.btnFilterAll, R.id.btnFilterWeek, R.id.btnFilterMonth,
            R.id.btnFilterBirthday, R.id.btnFilterAnniversary)
        all.forEach { id ->
            val btn = findViewById<Button>(id)
            if (id == activeId) {
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF6B4EFF.toInt())
                btn.setTextColor(Color.WHITE)
            } else {
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFEDE9FF.toInt())
                btn.setTextColor(0xFF6B4EFF.toInt())
            }
        }
    }

    private fun setupCalendarStrip() {
        val strip = findViewById<LinearLayout>(R.id.calendarStrip)
        strip.removeAllViews()
        val cal = Calendar.getInstance()
        val monthNames = listOf("Jan","Feb","Mar","Apr","May","Jun",
            "Jul","Aug","Sep","Oct","Nov","Dec")
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)

        // Show next 30 days
        for (i in 0 until 30) {
            val dayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val day = dayCal.get(Calendar.DAY_OF_MONTH)
            val month = dayCal.get(Calendar.MONTH)
            val year = dayCal.get(Calendar.YEAR)
            val isToday = i == 0

            // Check if any contact has event on this day
            val hasEvent = contacts.any {
                try {
                    val parts = it.date.split("-")
                    parts[1].toInt() - 1 == month && parts[2].toInt() == day
                } catch (e: Exception) { false }
            }

            val dayView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val dp = (56 * resources.displayMetrics.density).toInt()
                val dp4 = (4 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(dp, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp4
                }
                setPadding(dp4 * 2, dp4 * 2, dp4 * 2, dp4 * 2)
                setBackgroundColor(when {
                    isToday -> 0xFF6B4EFF.toInt()
                    hasEvent -> 0xFFEDE9FF.toInt()
                    else -> Color.TRANSPARENT
                })
            }

            val dayNumTv = TextView(this).apply {
                text = day.toString()
                textSize = 16f
                setTextColor(if (isToday) Color.WHITE else 0xFF1A1A2E.toInt())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }

            val dayNameTv = TextView(this).apply {
                val days = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
                text = days[dayCal.get(Calendar.DAY_OF_WEEK) - 1]
                textSize = 10f
                setTextColor(if (isToday) 0xFFC4B5FD.toInt() else 0xFF888899.toInt())
                gravity = Gravity.CENTER
            }

            val dotTv = TextView(this).apply {
                text = if (hasEvent) "●" else " "
                textSize = 8f
                setTextColor(if (isToday) Color.WHITE else 0xFFFF6B9D.toInt())
                gravity = Gravity.CENTER
            }

            dayView.addView(dayNameTv)
            dayView.addView(dayNumTv)
            dayView.addView(dotTv)
            strip.addView(dayView)
        }

        // Update month header
        val monthYearStr = "${monthNames[currentMonth]} $currentYear"
        val eventCount = contacts.count { daysUntil(it.date) <= 30 }
        findViewById<TextView>(R.id.tvCalMonth).text = monthYearStr
        findViewById<TextView>(R.id.tvEventCount).text = "$eventCount events this month"
    }

    private fun askUserName() {
        val input = EditText(this).apply {
            hint = "Your name e.g. Sandip"
            textSize = 16f
            setPadding(48, 24, 48, 24)
            setText(prefs.getString("user_name", ""))
        }
        AlertDialog.Builder(this)
            .setTitle("What's your name? 👋")
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

    private fun updateList() {
        filtered.clear()
        var list = contacts.sortedWith(compareBy(
            { daysUntil(it.date) },
            { it.date.split("-").getOrNull(1)?.toIntOrNull() ?: 0 },
            { it.date.split("-").getOrNull(2)?.toIntOrNull() ?: 0 }
        ))
        when (currentFilter) {
            "week" -> list = list.filter { daysUntil(it.date) <= 7 }
            "month" -> list = list.filter { daysUntil(it.date) <= 30 }
            "birthday" -> list = list.filter { it.cat == "birthday" }
            "anniversary" -> list = list.filter { it.cat == "anniversary" }
        }
        filtered.addAll(list)
        adapter.notifyDataSetChanged()
        updateEmptyState()
        val todayCount = contacts.count { daysUntil(it.date) == 0 }
        val title = when {
            todayCount > 0 -> "Today's Celebrations 🎉 ($todayCount)"
            currentFilter == "week" -> "This Week 📅"
            currentFilter == "month" -> "This Month 📅"
            currentFilter == "birthday" -> "All Birthdays 🎂"
            currentFilter == "anniversary" -> "All Anniversaries 💑"
            else -> "Upcoming Occasions 📅"
        }
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
        startActivity(Intent(this, WishActivity::class.java).apply {
            putExtra("contact_id", contact.id)
        })
    }

    private fun showEditDeleteDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(contact.name)
            .setItems(arrayOf("✏️ Edit", "🗑️ Delete")) { _, which ->
                when (which) {
                    0 -> startActivityForResult(
                        Intent(this, AddOccasionActivity::class.java).apply {
                            putExtra("edit_id", contact.id)
                        }, 100)
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
                setupCalendarStrip()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun scheduleAllReminders() { contacts.forEach { scheduleYearlyReminder(it) } }

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

            holder.tvIcon.text = if (c.cat == "anniversary") "💑" else "🎂"
            holder.tvName.text = c.name
            val catLabel = if (c.cat == "anniversary") "Anniversary" else "Birthday"
            holder.tvOccasion.text = "$catLabel · ⏰ ${fmtTime(c.remindHour, c.remindMin)}"
            val ageStr = when {
                years > 0 && c.cat == "birthday" -> " · Turning $years"
                years > 0 && c.cat == "anniversary" -> " · ${ordinal(years)} year"
                else -> ""
            }
            holder.tvDate.text = formatDateDisplay(c.date) + ageStr
            holder.tvDays.text = when (days) {
                0 -> "🎉 Today!"; 1 -> "Tomorrow!"; else -> "in $days days"
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

        private fun ordinal(n: Int): String {
            val s = when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"
                n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
            return "$n$s"
        }
    }
}
