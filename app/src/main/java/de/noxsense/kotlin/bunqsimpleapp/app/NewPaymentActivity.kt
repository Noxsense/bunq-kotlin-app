package de.noxsense.kotlin.bunqsimpleapp.app

import com.bunq.sdk.context.ApiContext
import com.bunq.sdk.context.BunqContext
import com.bunq.sdk.exception.ApiException
import com.bunq.sdk.exception.BunqException
import com.bunq.sdk.model.generated.`object`.Amount
import com.bunq.sdk.model.generated.`object`.Pointer
import com.bunq.sdk.model.generated.endpoint.Payment
import com.bunq.sdk.model.generated.endpoint.SandboxUser
import com.bunq.sdk.model.generated.endpoint.User
import com.bunq.sdk.model.generated.endpoint.UserPerson

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.*

import kotlinx.android.synthetic.main.activity_new_payment.*

class NewPaymentActivity : AppCompatActivity() {

	private val TAG = "Bunq-NewPayment"

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_new_payment)

		Log.d(TAG, "Opened new Acitivity.")

		new_payment_title.text = getString(R.string.new_payment_title)

		ArrayAdapter.createFromResource(
			this,
			R.array.new_payment_pointer_type_items,
			android.R.layout.simple_spinner_item
		).also {
			it.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item)
			new_payment_pointer_type.adapter = it
		}

		ArrayAdapter.createFromResource(
			this,
			R.array.currency_codes_ISO_4217,
			android.R.layout.simple_spinner_item
		).also {
			it.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item)
			new_payment_amount_currency.adapter = it
			new_payment_amount_currency.setSelection(48)
		}

		/* Confirm the inserted data.
		 * On success: "Back" to overview.
		 * On fail: Stay and make hints.  */
		new_payment_confirm.setOnClickListener {
			Log.d(TAG, "Confirmed payment entries.")
			new_payment_error.visibility = View.INVISIBLE

			try {
				// fetch inserted data.
				Log.d(TAG, "Fetch the inserted data.")
				val pointerType = new_payment_pointer_type.getSelectedItem().toString()
				val pointerValue = new_payment_pointer_value.text.toString()
				val amount = new_payment_amount.text.toString().toInt()
				val description = new_payment_description.text.toString()

				Log.d(TAG, "Input was: ($pointerType, $pointerValue) ($amount) ($description)")

				// make the payment with the API.
				runBlocking {
					launch(Dispatchers.Default) {
						val paymentId = makePayment(
							pointerType, pointerValue, amount, description)

						// Toast.makeText(this@NewPaymentActivity, "Payment suceeded.", Toast.LENGTH_LONG).show()

						Log.d(TAG, "Store to Context.")
						val apiContext = BunqContext.getApiContext()
						MainActivity.storeContext(this@NewPaymentActivity, apiContext)
						apiContext.ensureSessionActive()
						BunqContext.loadApiContext(apiContext)
					}
				}

				// return to MainActivity
				Log.d(TAG, "Back to MainActivity on Success.")
				this@NewPaymentActivity.onBackPressed()

			} catch (e: Exception) {
				Log.w(TAG, e.toString())

				when {
					e is BunqException || e is ApiException -> {
						val lines = e.message!!.split("\\n|\\r")

						new_payment_error.text = lines[lines.size - 1]
						new_payment_error.visibility = View.VISIBLE
						// Toast.makeText(this@NewPaymentActivity, "Payment failed.", Toast.LENGTH_LONG).show()
					}
					else -> {
						Log.e(TAG, "$e has no solution (Unknown error occurred.).")
						// Toast.makeText(this@NewPaymentActivity, "Unknown error occurred.", Toast.LENGTH_LONG).show()
					}
				}
			}
		}
	}

	/** Make a new payment.
	  * @return true on success. */
	private fun makePayment(
			pointerType: String,
			pointerValue: String,
			amount: Int,
			description: String
	) : Int {
		Log.d(TAG, "Load API context.")
		val apiContext = MainActivity.restoreContext(this@NewPaymentActivity)
		BunqContext.loadApiContext(apiContext)

		/* Generated Payee. */
		Log.d(TAG, "Try to make the payment.")
		val bunqResponse = Payment.create(
			Amount("${amount / 100.0}", "EUR"),
			Pointer(pointerType, pointerValue),
			description
		)

		Log.d(TAG, "Store the new API context.")
		MainActivity.storeContext(this@NewPaymentActivity, apiContext)
		apiContext.ensureSessionActive()
		BunqContext.loadApiContext(apiContext)

		Log.d(TAG, "Payment Succeeded.")

		return bunqResponse.getValue()
	}

	private fun backToOverview() {
		val intent = Intent(this, MainActivity::class.java)
		startActivity(intent)
	}
}
