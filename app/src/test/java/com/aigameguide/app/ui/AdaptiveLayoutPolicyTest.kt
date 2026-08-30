package com.aigameguide.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutPolicyTest {
    @Test
    fun compactPhoneUsesSinglePane() {
        assertFalse(AdaptiveLayoutPolicy.shouldUseTwoPane(420, false, false))
    }

    @Test
    fun unfoldedFoldUsesTwoPanes() {
        assertTrue(AdaptiveLayoutPolicy.shouldUseTwoPane(730, true, true))
    }

    @Test
    fun wideMediumWindowUsesTwoPanes() {
        assertTrue(AdaptiveLayoutPolicy.shouldUseTwoPane(700, true, false))
    }

    @Test
    fun listPaneStaysWithinRequestedRange() {
        assertEquals(0.40f, AdaptiveLayoutPolicy.listPaneFraction(700))
        assertEquals(0.38f, AdaptiveLayoutPolicy.listPaneFraction(900))
        assertEquals(0.36f, AdaptiveLayoutPolicy.listPaneFraction(1_300))
    }
}
