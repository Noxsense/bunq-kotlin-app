package de.noxsense.kotlin.bunqsimpleapp.app

import com.bunq.sdk.context.ApiContext
import com.bunq.sdk.context.BunqContext
import com.bunq.sdk.exception.BunqException
import com.bunq.sdk.model.generated.`object`.Amount
import com.bunq.sdk.model.generated.`object`.Pointer
import com.bunq.sdk.model.generated.endpoint.Payment
import com.bunq.sdk.model.generated.endpoint.SandboxUser

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.android.synthetic.main.activity_new_payment.*

class NewPaymentActivity : AppCompatActivity() {

	private val TAG = "Nox-Bunq-NewPaymentActivity"

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_new_payment)

		Log.d(TAG, "Opened new Acitivity.")

		val bundle: Bundle = intent.extras!!

		new_payment_title.text = getString(R.string.new_payment_title)

		/* Confirm the inserted data.
		 * On success: "Back" to overview.
		 * On fail: Stay and make hints.  */
		new_payment_confirm.setOnClickListener {
			Log.d(TAG, "Confirmed payment entries.")
			new_payment_title.text = "confirmed"

			// fetch inserted data.
			Log.d(TAG, "Fetch the inserted data.")

			// make the payment with the API.
			try {
				Log.d(TAG, "Try to make the payment.")
				val apiContext = ApiContext.restore(MainActivity.confFile)
				BunqContext.loadApiContext(apiContext)

				Payment.create(
					Amount("0.01", "EUR"),
					Pointer("EMAIL", "noxsense@gmail.com"),
					"Test Payment from Nox' Lil' Bunq App"
					)
			} catch (e: Exception) {
				Log.e(TAG, e.toString())
				if (e is BunqException) {
				} else {
				}
			}

			/* Send payment. */
			if (true) {
				Log.d(TAG, "Back to Bunq-MainActivity.")
				// Success -> Return to Main.
				Toast.makeText(this, "Payment suceeded.", Toast.LENGTH_LONG).show()
				backToOverview()
			} else {
				// hint possible errors.
				Log.d(TAG, "Stay to correct inserted data.")
				Toast.makeText(this, "Payment failed", Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun backToOverview() {
		val intent = Intent(this, MainActivity::class.java)
		startActivity(intent)
	}
}
