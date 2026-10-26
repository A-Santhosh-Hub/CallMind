package com.example.callmind

import com.example.callmind.core.blocking.BlockDecision
import com.example.callmind.core.blocking.BlockingSettings
import com.example.callmind.data.local.entities.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDecisionTest {

    private fun decide(
        number: String,
        settings: BlockingSettings = BlockingSettings(),
        block: List<String> = emptyList(),
        trust: List<String> = emptyList(),
        saved: Boolean? = false,
        recent: List<String> = emptyList()
    ) = BlockDecision.decide(number, settings, block, trust, saved, recent)

    @Test fun nothingEnabled_allowsEveryone() {
        assertFalse(decide("+919876543210").block)
        assertFalse(decide("").block)
    }

    @Test fun blocklistedNumber_isBlocked_evenWhenSettingsOff() {
        val d = decide("+91 98765 43210", block = listOf("9876543210"))
        assertTrue(d.block)
        assertEquals(BlockReason.BLOCKLIST, d.reason)
    }

    @Test fun trustlist_beatsBlocklistAndUnsavedRule() {
        val s = BlockingSettings(blockUnsaved = true)
        val d = decide("9876543210", s, block = listOf("9876543210"), trust = listOf("+919876543210"))
        assertFalse(d.block)
    }

    @Test fun unsavedRule_blocksStrangers_butNotContacts() {
        val s = BlockingSettings(blockUnsaved = true)
        assertEquals(BlockReason.UNSAVED, decide("9000011111", s, saved = false).reason)
        assertFalse(decide("9000011111", s, saved = true).block)
    }

    @Test fun unknownContactStatus_neverBlocksAsUnsaved() {
        val s = BlockingSettings(blockUnsaved = true)
        assertFalse(decide("9000011111", s, saved = null).block)
    }

    @Test fun emptyNumber_blockedOnlyWhenUnknownRuleOn() {
        assertTrue(decide("", BlockingSettings(blockUnknown = true)).block)
        assertFalse(decide("", BlockingSettings(blockUnknown = false)).block)
        assertEquals(BlockReason.UNKNOWN_NUMBER, decide("", BlockingSettings(blockUnknown = true)).reason)
    }

    @Test fun repeatCaller_isAllowedThroughOnSecondCall() {
        val s = BlockingSettings(blockUnsaved = true, allowRepeat = true)
        assertFalse(decide("9000011111", s, recent = listOf("+919000011111")).block)
    }

    @Test fun repeatException_canBeSwitchedOff() {
        val s = BlockingSettings(blockUnsaved = true, allowRepeat = false)
        assertTrue(decide("9000011111", s, recent = listOf("9000011111")).block)
    }

    @Test fun repeatException_neverAppliesToBlocklist() {
        val s = BlockingSettings(allowRepeat = true)
        assertTrue(decide("9000011111", s, block = listOf("9000011111"), recent = listOf("9000011111")).block)
    }

    @Test fun numberMatching_handlesCountryCodes_andRejectsShortCollisions() {
        assertTrue(BlockDecision.numbersMatch("+91 98765 43210", "09876543210"))
        assertTrue(BlockDecision.numbersMatch("9876543210", "919876543210"))
        assertFalse(BlockDecision.numbersMatch("98765", "12345"))
        assertFalse(BlockDecision.numbersMatch("123", "0123")) // too short to suffix-match
        assertFalse(BlockDecision.numbersMatch("", "9876543210"))
        assertFalse(BlockDecision.numbersMatch("9876543210", "9876543211"))
    }
}
