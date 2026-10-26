package com.example.callmind

import com.example.callmind.core.phone.T9
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {
    @Test
    fun `digits match the start of a name`() {
        assertTrue(T9.matchesName("Kamal", "526"))
        assertTrue(T9.matchesName("Kamalahasan S", "5262"))
    }

    @Test
    fun `any word of the name can match`() {
        assertTrue(T9.matchesName("Santh Kumar", "586")) // KUM
        assertTrue(T9.matchesName("Kumar, Arun", "2786")) // ARUN
    }

    @Test
    fun `whole name run together matches across words`() {
        assertTrue(T9.matchesName("Ab Cd", "2223")) // ABCD
    }

    @Test
    fun `first characters of consecutive words match`() {
        // J=5, 2, M=6: "J 296 Muruga"
        assertEquals(listOf(0..0, 2..2, 6..6), T9.matchRanges("J 296 Muruga", "526"))
    }

    @Test
    fun `a word start is highlighted as one range`() {
        assertEquals(listOf(6..8), T9.matchRanges("Santh Kumar", "586"))
    }

    @Test
    fun `accents are ignored`() {
        assertTrue(T9.matchesName("Émile", "36"))
        assertEquals(listOf(0..1), T9.matchRanges("Émile", "36"))
    }

    @Test
    fun `non matching or invalid input is rejected`() {
        assertFalse(T9.matchesName("Kamal", "527"))
        assertFalse(T9.matchesName("Kamal", ""))
        assertFalse(T9.matchesName("Kamal", "5*2"))
        assertFalse(T9.matchesName("Kamal", "+9198"))
        assertNull(T9.matchRanges("Kamal", "999"))
    }
}
