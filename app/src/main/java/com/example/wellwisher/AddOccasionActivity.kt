package com.example.wellwisher

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.*

class AddOccasionActivity : AppCompatActivity() {

    private var selectedCat = "birthday"
    private var selectedEmoji = "few"
    private var selYear = -1; private var selMonth = -1; private var selDay = -1
    private var selHour = 9; private var selMin = 0
    private var editingId: String? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> loadContactFromUri(uri) }
        }
    }

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openContactPicker()
        else Toast.makeText(this, "Permission denied. Type number manually.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_occasion)

        editingId = intent.getStringExtra("edit_id")
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        if (editingId != null) {
            tvTitle.text = "Edit Occasion"
            loadExisting(editingId!!)
        }

        findViewById<android.view.View>(R.id.btnClose).setOnClickListener { finish() }

        findViewById<android.widget.LinearLayout>(R.id.btnOccBirthday)
            .setOnClickListener { selectCat("birthday") }
        findViewById<android.widget.LinearLayout>(R.id.btnOccAnniversary)
            .setOnClickListener { selectCat("anniversary") }

        findViewById<android.widget.LinearLayout>(R.id.btnEmojiNone)
            .setOnClickListener { selectEmoji("none") }
        findViewById<android.widget.LinearLayout>(R.id.btnEmojiFew)
            .setOnClickListener { selectEmoji("few") }
        findViewById<android.widget.LinearLayout>(R.id.btnEmojiLots)
            .setOnClickListener { selectEmoji("lots") }

        findViewById<android.widget.LinearLayout>(R.id.btnPickContact)
            .setOnClickListener {
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED -> openContactPicker()
                    else -> contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }

        findViewById<android.view.View>(R.id.datePickerRow).setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selYear = y; selMonth = m; selDay = d
                val ds = "$y-${(m+1).toString().padStart(2,'0')}-${d.toString().padStart(2,'0')}"
                findViewById<EditText>(R.id.etDate).setText(ds)
                val tv = findViewById<TextView>(R.id.tvDateDisplay)
                tv.text = formatDate(ds)
                tv.setTextColor(0xFF1A1A2E.toInt())
            }, if (selYear>0) selYear else cal.get(Calendar.YEAR),
                if (selMonth>=0) selMonth else cal.get(Calendar.MONTH),
                if (selDay>0) selDay else cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        findViewById<android.view.View>(R.id.timePickerRow).setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                selHour = h; selMin = m
                findViewById<EditText>(R.id.etHour).setText(h.toString())
                findViewById<EditText>(R.id.etMin).setText(m.toString())
                findViewById<TextView>(R.id.tvTimeDisplay).text = fmtTime(h, m)
            }, selHour, selMin, false).show()
        }

        findViewById<Button>(R.id.btnSaveDone).setOnClickListener {
            if (saveContact()) finish()
        }
        findViewById<Button>(R.id.btnSaveAddAnother).setOnClickListener {
            if (saveContact()) resetForm()
        }
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun loadContactFromUri(uri: Uri) {
        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ), null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(it.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phone = it.getString(it.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.Phone.NUMBER))
                    .replace(" ", "").replace("-", "")
                val etName = findViewById<EditText>(R.id.etName)
                if (etName.text.isEmpty()) etName.setText(name)
                findViewById<EditText>(R.id.etPhone).setText(phone)
                Toast.makeText(this, "Contact loaded: $name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectCat(cat: String) {
        selectedCat = cat
        val purple = 0xFF6B4EFF.toInt()
        val white = 0xFFFFFFFF.toInt()
        val btnB = findViewById<android.widget.LinearLayout>(R.id.btnOccBirthday)
        val btnA = findViewById<android.widget.LinearLayout>(R.id.btnOccAnniversary)
        btnB.setBackgroundColor(if (cat == "birthday") purple else white)
        btnA.setBackgroundColor(if (cat == "anniversary") purple else white)
        (btnB.getChildAt(1) as TextView).setTextColor(if (cat == "birthday") white else purple)
        (btnA.getChildAt(1) as TextView).setTextColor(if (cat == "anniversary") white else purple)
        findViewById<EditText>(R.id.etCat).setText(cat)
    }

    private fun selectEmoji(emoji: String) {
        selectedEmoji = emoji
        val purple = 0xFF6B4EFF.toInt()
        val white = 0xFFFFFFFF.toInt()
        val btnN = findViewById<android.widget.LinearLayout>(R.id.btnEmojiNone)
        val btnF = findViewById<android.widget.LinearLayout>(R.id.btnEmojiFew)
        val btnL = findViewById<android.widget.LinearLayout>(R.id.btnEmojiLots)
        btnN.setBackgroundColor(if (emoji == "none") purple else white)
        btnF.setBackgroundColor(if (emoji == "few") purple else white)
        btnL.setBackgroundColor(if (emoji == "lots") purple else white)
        (btnN.getChildAt(0) as TextView).setTextColor(if (emoji == "none") white else purple)
        (btnF.getChildAt(0) as TextView).setTextColor(if (emoji == "few") white else purple)
        (btnL.getChildAt(0) as TextView).setTextColor(if (emoji == "lots") white else purple)
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

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (date.isEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return false
        }

        val prefs = getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        val id = editingId ?: UUID.randomUUID().toString()

        if (editingId != null) {
            for (i in 0 until arr.length()) {
                if (arr.getJSONObject(i).optString("id") == editingId) {
                    val o = arr.getJSONObject(i)
                    o.put("name", name); o.put("phone", phone)
                    o.put("cat", cat); o.put("date", date)
                    o.put("remindHour", hour); o.put("remindMin", min)
                    o.put("emojiStyle", emoji)
                    break
                }
            }
        } else {
            arr.put(org.json.JSONObject().apply {
                put("id", id); put("name", name); put("phone", phone)
                put("cat", cat); put("date", date)
                put("remindHour", hour); put("remindMin", min)
                put("emojiStyle", emoji); put("wishIndex", 0)
                put("personalTouch", "")
            })
        }
        prefs.edit().putString("contacts", arr.toString()).apply()

        // Schedule yearly reminder
        val alarmIntent = Intent(this, BirthdayReceiver::class.java).apply {
            putExtra("name", name); putExtra("cat", cat); putExtra("contact_id", id)
        }
        val pi = android.app.PendingIntent.getBroadcast(
            this, id.hashCode(), alarmIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        try {
            val parts = date.split("-")
            val now = Calendar.getInstance()
            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.YEAR, 1)
            }
            am.setRepeating(android.app.AlarmManager.RTC_WAKEUP,
                cal.timeInMillis, 365L * 24 * 60 * 60 * 1000, pi)
        } catch (e: Exception) { e.printStackTrace() }

        setResult(RESULT_OK)
        Toast.makeText(this,
            "Saved! Yearly reminder set at ${fmtTime(hour, min)}",
            Toast.LENGTH_LONG).show()
        return true
    }

    private fun resetForm() {
        editingId = null
        findViewById<EditText>(R.id.etName).setText("")
        findViewById<EditText>(R.id.etPhone).setText("")
        findViewById<EditText>(R.id.etDate).setText("")
        val tvDate = findViewById<TextView>(R.id.tvDateDisplay)
        tvDate.text = "Tap to select date"
        tvDate.setTextColor(0xFFAAAAAA.toInt())
        selYear = -1; selMonth = -1; selDay = -1
        selectCat("birthday")
        selectEmoji("few")
        selHour = 9; selMin = 0
        findViewById<EditText>(R.id.etHour).setText("9")
        findViewById<EditText>(R.id.etMin).setText("0")
        findViewById<TextView>(R.id.tvTimeDisplay).text = "9:00 AM"
        findViewById<TextView>(R.id.tvTitle).text = "Add Occasion"
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
                        selYear = p[0].toInt()
                        selMonth = p[1].toInt() - 1
                        selDay = p[2].toInt()
                    } catch (e: Exception) {}
                }
                val h = o.optInt("remindHour", 9)
                val m = o.optInt("remindMin", 0)
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
