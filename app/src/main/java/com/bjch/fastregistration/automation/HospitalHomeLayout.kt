package com.bjch.fastregistration.automation

/** Stable proportional positions measured from the hospital mini-program home screen. */
internal object HospitalHomeLayout {
    private const val REGISTRATION_CARD_CENTER_X_RATIO = 0.735f
    private const val REGISTRATION_CARD_CENTER_Y_RATIO = 0.3225f

    fun registrationCardTapPoint(screenWidth: Int, screenHeight: Int): Pair<Float, Float> =
        screenWidth * REGISTRATION_CARD_CENTER_X_RATIO to
            screenHeight * REGISTRATION_CARD_CENTER_Y_RATIO
}

