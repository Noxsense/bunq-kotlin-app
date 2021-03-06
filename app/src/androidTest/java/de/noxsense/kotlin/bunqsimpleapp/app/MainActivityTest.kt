package de.noxsense.kotlin.bunqsimpleapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

	@get:Rule
	var activityRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java)

	@Test
	fun refreshButton() {
		// is prohibited offline
		// is pulling new bunq data => maybe also updating the listview and balances
	}

	@Test
	fun newPaymentView() {
		// is prohibited offline
		// changes the view
	}

	@Test
	fun resumeAndPause() {
		// store context on pause
		// restore context on resume
	}
}
