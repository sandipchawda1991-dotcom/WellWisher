package com.example.wellwisher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class AddOccasionActivity : AppCompatActivity() {

    private var selectedCat = "birthday"
    private var selectedEmoji = "few"
    private var selYear = -1; private var selMonth = -1; private var selDay = -1
    private var selHour = 9; private var selMin = 0
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_occasion)

        editingId = intent.getStringExtra("edit_id")
        val tvTitle = findViewById<TextView>(R.id.tvTitle)

        if (editingId != null) {
            tvTitle.text = "✏️ Edit Occasion"
            loadExisting(editingId!!)
        }

        findViewById<android.view.View>(R.id.btnClose).setOnClickListener { finish() }

        // Occasion type selection
        val btnBirthday = findViewById<android.widget.LinearLayout>(R.id.btnOccBirthday)
        val btnAnniversary = findViewById<android.widget.LinearLayout>(R.id.btnOccAnniversary)
        btnBirthday.setOnClickListener { selectCat("birthday") }
        btnAnniversary.setOnClickListener { selectCat("anniversary") }

        // Emoji selection
        val btnNone = findViewById<android.widget.LinearLayout>(R.id.btnEmojiNone)
        val btnFew = findViewById<android.widget.LinearLayout>(R.id.btnEmojiFew)
        val btnLots = findViewById<android.widget.LinearLayout>(R.id.btnEmojiLots)
        btnNone.setOnClickListener { selectEmoji("none") }
        btnFew.setOnClickListener { selectEmoji("few") }
        btnLots.setOnClickListener { selectEmoji("lots") }

        // Date picker
        findViewById<android.view.View>(R.id.datePickerRow).setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selYear = y; selMonth = m; selDay = d
                val ds = "$y-${(m+1).toString().padStart(2,'0')}-${d.toString().padStart(2,'0')}"
                findViewById<EditText>(R.id.etDate).setText(ds)
                val disp = formatDate(ds)
                val tv = findViewById<TextView>(R.id.tvDateDisplay)
                tv.text = disp
                tv.setTextColor(0xFF1A1A2E.toInt())
            }, if (selYear>0) selYear else cal.get(Calendar.YEAR),
                if (selMonth>=0) selMonth else cal.get(Calendar.MONTH),
                if (selDay>0) selDay else cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Time picker
        findViewById<android.view.View>(R.id.timePickerRow).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h; selMin = m
                findViewById<EditText>(R.id.etHour).setText(h.toString())
                findViewById<EditText>(R.id.etMin).setText(m.toString())
                findViewById<TextView>(R.id.tvTimeDisplay).text = fmtTime(h, m)
            }, selHour, selMin, false).show()
        }

        findViewById<android.widget.Button>(R.id.btnSaveDone).setOnClickListener {
            if (saveContact()) finish()
        }

        findViewById<android.widget.Button>(R.id.btnSaveAddAnother).setOnClickListener {
            if (saveContact()) {
                // Reset form
                findViewById<EditText>(R.id.etName).setText("")
                findViewById<EditText>(R.id.etPhone).setText("")
                findViewById<EditText>(R.id.etDate).setText("")
                findViewById<TextView>(R.id.tvDateDisplay).text = "Tap to select date"
                findViewById<TextView>(R.id.tvDateDisplay).setTextColor(0xFFAAAAAA.toInt())
                selYear = -1; selMonth = -1; selDay = -1
                editingId = null
                tvTitle.text = "✨ Add Occasion"
                Toast.makeText(this, "Saved! Add another one 😊", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectCat(cat: String) {
        selectedCat = cat
        val btnB = findViewById<android.widget.LinearLayout>(R.id.btnOccBirthday)
        val btnA = findViewById<android.widget.LinearLayout>(R.id.btnOccAnniversary)
        val purple = 0xFF6B4EFF.toInt()
        val white = 0xFFFFFFFF.toInt()
        if (cat == "birthday") {
            btnB.setBackgroundColor(purple)
            btnB.findViewWithTag<TextView?>(null).let {}
            btnA.setBackgroundColor(white)
        } else {
            btnA.setBackgroundColor(purple)
            btnB.setBackgroundColor(white)
        }
        findViewById<EditText>(R.id.etCat).setText(cat)
    }

    private fun selectEmoji(emoji: String) {
        selectedEmoji = emoji
        val purple = 0xFF6B4EFF.toInt()
        val white = 0xFFFFFFFF.toInt()
        findViewById<android.widget.LinearLayout>(R.id.btnEmojiNone).setBackgroundColor(if (emoji=="none") purple else white)
        findViewById<android.widget.LinearLayout>(R.id.btnEmojiFew).setBackgroundColor(if (emoji=="few") purple else white)
        findViewById<android.widget.LinearLayout>(R.id.btnEmojiLots).setBackgroundColor(if (emoji=="lots") purple else white)
        val noneText = findViewById<android.widget.LinearLayout>(R.id.btnEmojiNone).getChildAt(0) as TextView
        val fewText = findViewById<android.widget.LinearLayout>(R.id.btnEmojiFew).getChildAt(0) as TextView
        val lotsText = findViewById<android.widget.LinearLayout>(R.id.btnEmojiLots).getChildAt(0) as TextView
        noneText.setTextColor(if (emoji=="none") white else purple)
        fewText.setTextColor(if (emoji=="few") white else purple)
        lotsText.setTextColor(if (emoji=="lots") white else purple)
        findViewById<EditText>(R.id.etEmoji).setText(emoji)
    }

    private fun saveContact(): Boolean {
        val name = findViewById<EditText>(R.id.etName).text.toString().trim()
        val phone = findViewById<EditText>(R.id.etPhone).text.toString().trim()
        val date = findViewById<EditText>(R.id.etDate).text.toString().trim()
        val cat = findViewById<EditText>(R.id.etCat).text.toString()
        val emoji = findViewById<EditText>(R.id.etEmoji).text.toString()
        val hour = findViewById<EditText>(R.id.etHour).text.toString().toIntOrNull() ?: 9
        val min = findViewById<EditText>(R.id.etMin).text.toString().toIntOrNull() ?: 0

        if (name.isEmpty()) { Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show(); return false }
        if (date.isEmpty()) { Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show(); return false }

        val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        val id = editingId ?: UUID.randomUUID().toString()

        if (editingId != null) {
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("id") == editingId) {
                    val o = arr.getJSONObject(i)
                    o.put("name", name); o.put("phone", phone); o.put("cat", cat)
                    o.put("date", date); o.put("remindHour", hour); o.put("remindMin", min)
                    o.put("emojiStyle", emoji)
                    break
                }
            }
        } else {
            arr.put(org.json.JSONObject().apply {
                put("id", id); put("name", name); put("phone", phone); put("cat", cat)
                put("date", date); put("remindHour", hour); put("remindMin", min)
                put("emojiStyle", emoji); put("wishIndex", 0); put("personalTouch", "")
            })
        }
        prefs.edit().putString("contacts", arr.toString()).apply()

        // Schedule reminder
        val alarmIntent = Intent(this, BirthdayReceiver::class.java).apply {
            putExtra("name", name); putExtra("cat", cat); putExtra("contact_id", id)
        }
        val pi = android.app.PendingIntent.getBroadcast(this, id.hashCode(), alarmIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        try {
            val parts = date.split("-")
            val now = Calendar.getInstance()
            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.YEAR, 1)
            }
            am.setRepeating(android.app.AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                365L * 24 * 60 * 60 * 1000, pi)
        } catch (e: Exception) { e.printStackTrace() }

        setResult(RESULT_OK)
        Toast.makeText(this, "✅ ${name} saved! Yearly reminder set at ${fmtTime(hour, min)}", Toast.LENGTH_LONG).show()
        return true
    }

    private fun loadExisting(id: String) {
        val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == id) {
                findViewById<EditText>(R.id.etName).setText(o.optString("name"))
                findViewById<EditText>(R.id.etPhone).setText(o.optString("phone"))
                val date = o.optString("date")
                findViewById<EditText>(R.id.etDate).setText(date)
                if (date.isNotEmpty()) {
                    val tv = findViewById<TextView>(R.id.tvDateDisplay)
                    tv.text = formatDate(date)
                    tv.setTextColor(0xFF1A1A2E.toInt())
                    try {
                        val p = date.split("-")
                        selYear = p[0].toInt(); selMonth = p[1].toInt()-1; selDay = p[2].toInt()
                    } catch (e: Exception) {}
                }
                val h = o.optInt("remindHour", 9); val m = o.optInt("remindMin", 0)
                selHour = h; selMin = m
                findViewById<EditText>(R.id.etHour).setText(h.toString())
                findViewById<EditText>(R.id.etMin).setText(m.toString())
                findViewById<TextView>(R.id.tvTimeDisplay).text = fmtTime(h, m)
                selectCat(o.optString("cat", "birthday"))
                selectEmoji(o.optString("emojiStyle", "few"))
                break
            }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parts = dateStr.split("-")
            val months = listOf("Jan","Feb","Mar","Apr","May","Jun",
                "Jul","Aug","Sep","Oct","Nov","Dec")
            "${months[parts[1].toInt()-1]} ${parts[2].toInt()}, ${parts[0]}"
        } catch (e: Exception) { dateStr }
    }

    private fun fmtTime(h: Int, m: Int): String {
        val ampm = if (h >= 12) "PM" else "AM"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${m.toString().padStart(2,'0')} $ampm"
    }
}
