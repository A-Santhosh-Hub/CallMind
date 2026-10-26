package com.example.callmind

import com.example.callmind.data.local.entities.CallRecordEntity
import com.example.callmind.data.local.entities.CallType
import com.example.callmind.data.repository.RecordingItem
import com.example.callmind.data.repository.RecordingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class RecordingMatchTest {
    private fun time(text: String) = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).parse(text)!!.time

    private fun phoneRec(label: String, at: String, number: String? = null) = RecordingItem(
        key = "phone:1", uri = "content://x/1", label = label, number = number,
        startedAt = time(at), durationMs = 13_000, fromPhone = true
    )

    private fun call(number: String, name: String?, at: String, seconds: Long) = CallRecordEntity(
        phoneNumber = number, contactName = name, durationSeconds = seconds, timestamp = time(at), type = CallType.OUTGOING
    )

    @Test
    fun `phone recorder file names are parsed`() {
        val (label, at) = RecordingsRepository.parsePhoneRecordingName("Alex #office 2026-10-26 17-15-22.m4a")!!
        assertEquals("Alex #office", label)
        assertEquals(time("2026-10-26 17-15-22"), at)
        val (num, _) = RecordingsRepository.parsePhoneRecordingName("+914443039039 2026-10-23 10-23-27.m4a")!!
        assertEquals("+914443039039", num)
        assertNull(RecordingsRepository.parsePhoneRecordingName("voice note.m4a"))
    }

    @Test
    fun `a named recording matches the call it happened in`() {
        val rec = phoneRec("Alex #office", "2026-10-26 17-15-22")
        assertTrue(RecordingsRepository.matches(call("+915551234567", "Alex #office", "2026-10-26 17-15-10", 13), rec))
    }

    @Test
    fun `a recording of another time does not match`() {
        val rec = phoneRec("Alex #office", "2026-10-26 17-15-22")
        assertFalse(RecordingsRepository.matches(call("+915551234567", "Alex #office", "2026-10-26 16-00-00", 13), rec))
    }

    @Test
    fun `a recording filed under a number matches by the last ten digits`() {
        val rec = phoneRec("+915551234567", "2026-10-26 17-15-22", number = "+915551234567")
        assertTrue(RecordingsRepository.matches(call("05551234567", null, "2026-10-26 17-15-12", 30), rec))
        assertFalse(RecordingsRepository.matches(call("+919840634544", null, "2026-10-26 17-15-12", 30), rec))
    }

    @Test
    fun `the first matching recording is found`() {
        val recs = listOf(phoneRec("Sam", "2026-10-26 10-00-00"), phoneRec("Alex #office", "2026-10-26 17-15-22"))
        assertNotNull(RecordingsRepository.forCall(call("+91", "Alex #office", "2026-10-26 17-15-10", 13), recs))
        assertNull(RecordingsRepository.forCall(call("+91", "Kamal", "2026-10-26 17-15-10", 13), recs))
    }
}
