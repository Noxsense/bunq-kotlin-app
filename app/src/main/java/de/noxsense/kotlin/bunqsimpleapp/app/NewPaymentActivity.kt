package de.noxsense.kotlin.bunqsimpleapp.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import de.noxsense.kotlin.bunqsimpleapp.library.User
import de.noxsense.kotlin.bunqsimpleapp.library.Payment
import de.noxsense.kotlin.bunqsimpleapp.library.android.NotificationUtil

import kotlinx.android.synthetic.main.activity_new_payment.*

class NewPaymentActivity : AppCompatActivity() {

	private val notificationUtil: NotificationUtil by lazy { NotificationUtil(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_new_payment)

		val bundle: Bundle = intent.extras!!

		val accessPoint : String? = bundle.getString("access_point")

		/* Get the user. */
		val sender = User(
			bundle.getString("user.iban") ?: "DE 0 0 0 0 0",
			bundle.getString("user.name") ?: "Sender")

		new_payment_title.text = getString(R.string.new_payment_title)

		/* Confirm the inserted data.
		 * On success: "Back" to overview.
		 * On fail: Stay and make hints.  */
		new_payment_confirm.setOnClickListener {
			new_payment_title.text = "confirmed"

			/* Get Data for the payment. */
			val paymentTodo = getPaymentTodo(sender)

			/* Send payment. */
			if (paymentTodo.send()) {
				// Success -> Return to Main.
				Toast.makeText(this, "Payment suceeded.", Toast.LENGTH_LONG).show()
				backToOverview(sender, accessPoint!!)
			} else {
				// hint possible errors.
				Toast.makeText(this, "Payment failed", Toast.LENGTH_LONG).show()
			}
		}
	}

	private fun getPaymentTodo(sender: User) : Payment {
		val receiver = User(
			new_payment_iban.text.toString(),
			new_payment_receiver.text.toString())

		val amount = try {
			new_payment_amount.text.toString().toInt()
		} catch (e: NumberFormatException) {
			0
		}

		val text = new_payment_text.text.toString()

		return Payment(sender, receiver, amount, text)
	}

	private fun backToOverview(user: User, accessPoint: String) {
		val intent = Intent(this, MainActivity::class.java)
		intent.putExtra("user.iban", user.iban)
		intent.putExtra("user.name", user.name)
		intent.putExtra("user.api", accessPoint)
		startActivity(intent)
	}
}
