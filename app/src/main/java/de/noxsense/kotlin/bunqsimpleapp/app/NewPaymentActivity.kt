package de.noxsense.kotlin.bunqsimpleapp.app

import com.bunq.sdk.context.BunqContext
import com.bunq.sdk.exception.ApiException
import com.bunq.sdk.exception.BunqException
import com.bunq.sdk.model.generated.`object`.Amount
import com.bunq.sdk.model.generated.`object`.Pointer
import com.bunq.sdk.model.generated.endpoint.Payment

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import kotlinx.android.synthetic.main.activity_new_payment.*

class NewPaymentActivity : AppCompatActivity() {

	private val TAG = "Bunq-NewPayment"

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_new_payment)

		Log.d(TAG, "Opened new Acitivity.")

		val simpleSpinner = android.R.layout.simple_spinner_item
		val simpleDropDown = android.R.layout.simple_spinner_dropdown_item

		new_payment_title.text = getString(R.string.new_payment_title)

		ArrayAdapter
			.createFromResource(
				this, R.array.new_payment_pointer_type_items, simpleSpinner)
			.also {
				it.setDropDownViewResource(simpleDropDown)
				new_payment_pointer_type.adapter = it
			}

		ArrayAdapter
			.createFromResource(
				this, R.array.currency_codes_ISO_4217, simpleSpinner)
			.also {
				it.setDropDownViewResource(simpleDropDown)
				new_payment_amount_currency.adapter = it
				new_payment_amount_currency.setSelection(48) // EUR
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
				val currency = new_payment_amount_currency.getSelectedItem().toString()
				val amount = new_payment_amount.text.toString().toInt()
				val description = new_payment_description.text.toString()

				Log.d(TAG, "Input was: ($pointerType, $pointerValue) ($amount) ($description)")

				// make the payment with the API.
				runBlocking {
					launch(Dispatchers.Default) {
						makePayment(
							pointerType, pointerValue,
							currency, amount,
							description)
					}
				}
				showToast("Payment successfully sent.")
				Thread.sleep(3000L) // sleep before the next request (MainActivity.onResume())

				// return to MainActivity
				Log.d(TAG, "Back to MainActivity on Success.")
				this@NewPaymentActivity.onBackPressed()

			} catch (e: Exception) {
				Log.w(TAG, e.toString())

				when {
					// Show Bunq Error, maybe they can be corrected.
					e is BunqException || e is ApiException -> {
						val lines = e.message!!.split("\\n\\|\\r")

						new_payment_error.text = lines[lines.size - 1]
						new_payment_error.visibility = View.VISIBLE
						showToast("Payment failed")
					}
					// Log and Toast other errors.
					else -> {
						Log.e(TAG, "$e has no solution (Unknown error occurred.).")
						showToast("Unknown error occurred.")
					}
				}
			}
		}
	}

	/** Make a new payment.
	  * @return ID of the BunqResponse.
	  * @throws BunqException when the Payment fails or the connection. */
	private fun makePayment(
			pointerType: String, pointerValue: String,
			currency: String, amount: Int,
			description: String
	) : Int {
		Log.d(TAG, "Load API context.")
		val apiContext = BunqHandle.restoreContext(this@NewPaymentActivity)
		BunqContext.loadApiContext(apiContext)

		/* Generated Payee. */
		Log.d(TAG, "Try to make the payment.")
		val bunqResponse = Payment.create(
			Amount("${amount / 100.0}", "${currency}"),
			Pointer(pointerType, pointerValue),
			description
		)

		Log.d(TAG, "Store the new API context.")
		BunqHandle.storeContext(context = this@NewPaymentActivity, apiContext = apiContext)
		apiContext.ensureSessionActive()
		BunqContext.updateApiContext(apiContext)

		Log.d(TAG, "Payment Succeeded.")

		return bunqResponse.getValue()
	}

	protected fun showToast(msg: Any?, duration: Int = Toast.LENGTH_LONG) {
		Toast.makeText(this.getApplicationContext(), "${msg}", duration).run {
			setGravity(Gravity.CENTER, 0, 0)
			show()
		}
	}
}
