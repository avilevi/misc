package com.hcexport

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

data class SyncSchedule(
    val id: String = UUID.randomUUID().toString(),
    val type: String,       // "daily" or "weekly"
    val hour: Int,
    val minute: Int,
    val days: Set<Int> = emptySet(), // 1=Mon..7=Sun, only for weekly
) {
    fun nextTriggerMs(): Long {
        val now = Calendar.getInstance()
        if (type == "daily") {
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!target.after(now)) target.add(Calendar.DAY_OF_MONTH, 1)
            return target.timeInMillis
        } else {
            val calDays = days.map { d -> if (d == 7) Calendar.SUNDAY else d + 1 }.toSet()
            for (i in 0..7) {
                val candidate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (i > 0) add(Calendar.DAY_OF_MONTH, i)
                }
                if (candidate.after(now) && candidate.get(Calendar.DAY_OF_WEEK) in calDays) {
                    return candidate.timeInMillis
                }
            }
            return now.timeInMillis + 7L * 24 * 60 * 60 * 1000
        }
    }

    fun nextTriggerDelayMs(): Long {
        val targetMs = nextTriggerMs()
        val nowMs = System.currentTimeMillis()
        return (targetMs - nowMs).coerceAtLeast(0L)
    }

    fun displayString(): String {
        val t = "%02d:%02d".format(hour, minute)
        return if (type == "daily") "Daily at $t"
        else {
            val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            "Weekly — ${days.sorted().joinToString(", ") { names[it - 1] }} at $t"
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("hour", hour)
        put("minute", minute)
        put("days", JSONArray(days.toList()))
    }

    companion object {
        fun fromJson(obj: JSONObject): SyncSchedule {
            val arr = obj.getJSONArray("days")
            return SyncSchedule(
                id     = obj.getString("id"),
                type   = obj.getString("type"),
                hour   = obj.getInt("hour"),
                minute = obj.getInt("minute"),
                days   = (0 until arr.length()).map { arr.getInt(it) }.toSet(),
            )
        }
    }
}
