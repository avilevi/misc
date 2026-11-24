package com.hcexport

import android.content.Context

/**
 * Removing a journal entry, for both the day list and the entry screen.
 *
 * The journal merges several sources, so deleting means more than hiding a row:
 * the app's own schedule (and the calendar event it created), the AI plan and
 * its values, a merged watch session, and a legacy WOD row are removed; the
 * key is remembered in [JournalHiddenStore] so a calendar event elsewhere or a
 * later sync cannot bring the entry straight back.
 */
object JournalDeleter {

    fun delete(ctx: Context, item: JournalItem) {
        JournalHiddenStore.hide(ctx, item.key)
        when (item) {
            is JournalItem.Workout -> {
                // The schedule this app wrote, and the event it created.
                ScheduledSessionStore.get(ctx, item.date, item.typeKey)?.let { schedule ->
                    schedule.eventId?.let { CalendarHelper.deleteEvent(ctx, it) }
                    ScheduledSessionStore.remove(ctx, item.date, item.typeKey)
                }
                // The AI plan and the values entered into it belong to the entry.
                InteractiveStore.delete(ctx, item.date, item.typeKey)
                // A watch session merged into this workout goes with it.
                item.biometrics.forEach { bio ->
                    JournalStorage.deleteEntry(ctx, bio.entryId)
                    JournalNotesStore.remove(ctx, JournalKeys.activity(bio.entryId))
                }
                // A WOD that only the journal remembers.
                item.sourceEntryId?.let { JournalStorage.deleteEntry(ctx, it) }
                JournalNotesStore.remove(ctx, item.key)
            }

            is JournalItem.Activity -> {
                JournalStorage.deleteEntry(ctx, item.entryId)
                JournalNotesStore.remove(ctx, item.key)
            }

            is JournalItem.Sleep -> {
                JournalStorage.deleteEntry(ctx, item.entryId)
                JournalNotesStore.remove(ctx, item.key)
            }
        }
    }

    fun confirmMessage(item: JournalItem): String = when (item) {
        is JournalItem.Workout ->
            "This removes the entry, its AI plan and values, and any schedule this app " +
                "created — including the calendar event it added. Calendar events created " +
                "elsewhere are left alone, and this workout stays hidden from the journal. " +
                "Records you set from it stay in Records until you delete them there."

        is JournalItem.Activity ->
            "This removes the entry and its comment from the journal. The recorded " +
                "session stays in Health Connect."

        is JournalItem.Sleep ->
            "This removes the night and its comment from the journal. The sleep data " +
                "stays in Health Connect."
    }
}
