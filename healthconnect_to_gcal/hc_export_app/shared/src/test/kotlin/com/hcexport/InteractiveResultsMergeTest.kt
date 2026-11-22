package com.hcexport

import org.junit.Assert.assertEquals
import org.junit.Test

class InteractiveResultsMergeTest {

    @Test
    fun onlyChangedFieldsAreApplied() {
        val base = mapOf(
            "e:0:0:0" to mapOf("weight" to "60", "reps" to "5"),
        )
        val current = mapOf(
            "e:0:0:0" to mapOf("weight" to "62.5", "reps" to "5"),
        )
        val phone = mapOf(
            "e:0:0:0" to mapOf("weight" to "65", "reps" to "5", "note" to "felt good"),
        )

        val merged = InteractiveResults.mergeChanges(phone, base, current)

        // The watch changed the weight; the phone's newer weight is replaced.
        assertEquals("62.5", merged["e:0:0:0"]?.get("weight"))
        // A field the watch never touched keeps the phone's value.
        assertEquals("felt good", merged["e:0:0:0"]?.get("note"))
        assertEquals("5", merged["e:0:0:0"]?.get("reps"))
    }

    @Test
    fun anUnchangedStaleDocumentCannotEraseThePhone() {
        val base = mapOf("s:0" to mapOf("time" to "12:00"))
        val current = base // the watch did nothing
        val phone = mapOf("s:0" to mapOf("time" to "11:42"))

        val merged = InteractiveResults.mergeChanges(phone, base, current)
        assertEquals("11:42", merged["s:0"]?.get("time"))
    }

    @Test
    fun aClearedFieldStaysCleared() {
        val base = mapOf("e:0:0:0" to mapOf("weight" to "60"))
        val current = mapOf("e:0:0:0" to mapOf("weight" to ""))
        val phone = mapOf("e:0:0:0" to mapOf("weight" to "60"))

        val merged = InteractiveResults.mergeChanges(phone, base, current)
        assertEquals("", merged["e:0:0:0"]?.get("weight"))
    }

    @Test
    fun aNewScopeIsAdded() {
        val base = emptyMap<String, Map<String, String>>()
        val current = mapOf("m:0" to mapOf("reached" to "e:0:0:1"))
        val phone = mapOf("e:0:0:0" to mapOf("reps" to "5"))

        val merged = InteractiveResults.mergeChanges(phone, base, current)
        assertEquals("e:0:0:1", merged["m:0"]?.get("reached"))
        assertEquals("5", merged["e:0:0:0"]?.get("reps"))
    }
}
