package com.aigameguide.app.ui

/** Pure layout decisions kept separate so Fold behavior can be unit-tested. */
internal object AdaptiveLayoutPolicy {
    const val TWO_PANE_MIN_WIDTH_DP = 680

    fun shouldUseTwoPane(
        widthDp: Int,
        mediumOrExpandedWindow: Boolean,
        hasVerticalFoldingFeature: Boolean
    ): Boolean = hasVerticalFoldingFeature ||
        (mediumOrExpandedWindow && widthDp >= TWO_PANE_MIN_WIDTH_DP)

    fun listPaneFraction(widthDp: Int): Float = when {
        widthDp >= 1_200 -> 0.36f
        widthDp >= 840 -> 0.38f
        else -> 0.40f
    }
}
