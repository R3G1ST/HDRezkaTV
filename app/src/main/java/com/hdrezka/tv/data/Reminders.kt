package com.hdrezka.tv.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hdrezka.tv.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Reminder(
    val id: String,
    val movieId: String,
    val title: String,
    val pageUrl: String,
    val posterUrl: String?,
    val label: String,
    val dateText: String,
    val timestampMs: Long
)

object RemindersStore {
    private const val PREFS = "hdrezka_reminders"

    fun list(context: Context): List<Reminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("items", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val result = ArrayList<Reminder>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val item = runCatching {
                Reminder(
                    id = row.getString("id"),
                    movieId = row.getString("movieId"),
                    title = row.getString("title"),
                    pageUrl = row.getString("pageUrl"),
                    posterUrl = row.optString("posterUrl").takeIf { it.isNotBlank() },
                    label = row.getString("label"),
                    dateText = row.optString("dateText"),
                    timestampMs = row.optLong("timestampMs")
                )
            }.getOrNull() ?: continue
            result.add(item)
        }
        return result.sortedBy { it.timestampMs }
    }

    fun contains(context: Context, id: String): Boolean =
        list(context).any { it.id == id }

    fun toggle(context: Context, reminder: Reminder): Boolean {
        val items = list(context).toMutableList()
        val existing = items.firstOrNull { it.id == reminder.id }
        if (existing != null) {
            items.removeAll { it.id == reminder.id }
            ReminderScheduler.cancel(context, reminder)
            persist(context, items)
            return false
        }
        items.add(reminder)
        persist(context, items)
        ReminderScheduler.schedule(context, reminder)
        return true
    }

    fun clear(context: Context) {
        for (item in list(context)) ReminderScheduler.cancel(context, item)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove("items").apply()
    }

    private fun persist(context: Context, items: List<Reminder>) {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("movieId", item.movieId)
                    .put("title", item.title)
                    .put("pageUrl", item.pageUrl)
                    .put("posterUrl", item.posterUrl ?: "")
                    .put("label", item.label)
                    .put("dateText", item.dateText)
                    .put("timestampMs", item.timestampMs)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("items", array.toString()).apply()
    }

    /// «16 августа 2011» → ближайшие 18:00 этого дня.
    fun timestamp(dateText: String, hour: Int = 18): Long {
        val months = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
        )
        val match = Regex("(\\d{1,2})\\s+([а-яё]+)\\s+(\\d{4})", RegexOption.IGNORE_CASE)
            .find(dateText) ?: return 0L
        val day = match.groupValues[1].toIntOrNull() ?: return 0L
        val monthName = match.groupValues[2].lowercase()
        val month = months.indexOf(monthName)
        if (month < 0) return 0L
        val year = match.groupValues[3].toIntOrNull() ?: return 0L
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

object ReminderScheduler {
    private const val CHANNEL = "hdrezka_reminders"

    fun schedule(context: Context, reminder: Reminder) {
        if (reminder.timestampMs <= System.currentTimeMillis()) return
        ensureChannel(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, reminder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.timestampMs,
                pending
            )
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, reminder.timestampMs, pending)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context, reminder))
    }

    private fun pendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", reminder.title)
            putExtra("label", reminder.label)
            putExtra("id", reminder.id.hashCode())
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = NotificationChannel(
            CHANNEL,
            "Напоминания о сериях",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    fun notifyNow(context: Context, title: String, label: String, id: Int) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Вышла новая серия: $label")
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title").orEmpty()
        val label = intent.getStringExtra("label").orEmpty()
        val id = intent.getIntExtra("id", title.hashCode())
        ReminderScheduler.notifyNow(context, title, label, id)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        for (reminder in RemindersStore.list(context)) {
            ReminderScheduler.schedule(context, reminder)
        }
    }
}
