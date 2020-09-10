package de.noxsense.kotlin.bunqsimpleapp.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import de.noxsense.kotlin.bunqsimpleapp.app.NewPaymentActivity
import de.noxsense.kotlin.bunqsimpleapp.library.Payment
import de.noxsense.kotlin.bunqsimpleapp.library.User
import de.noxsense.kotlin.bunqsimpleapp.library.android.NotificationUtil

import kotlinx.android.synthetic.main.activity_main.*
// import kotlinx.android.synthetic.main.activity_new_payment.*

class MainActivity : AppCompatActivity() {

	private val notificationUtil: NotificationUtil by lazy { NotificationUtil(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		var userApi: String? = "01234567"

		/* Login with Access Point. */
		if (userApi == null) {
			notificationUtil.showNotification(
				context = this,
				title = "Login Error",
				message = "No Access Point given.")
			return
		}

		/* Create user representation. */
		var user: User? = User("DE 1111 2222 3333 4444 55", "Alpha Bet")


		/* Fetch user's payments. And display them. */

		// mock payments to display something.
		var receiver = User("DE 1111 2222 3333 4444 56", "Beta Bet")
		user?.newPayment(receiver, 1 /*cent*/, "Test 0")
		user?.newPayment(receiver, 1 /*cent*/, "Test 1")
		user?.newPayment(receiver, 1 /*cent*/, "Test 2")
		user?.newPayment(receiver, 1 /*cent*/, "Test 3")
		user?.newPayment(receiver, 1 /*cent*/, "Test 4")

		user?.listUserPayments(this@MainActivity)

		/* Option to make a new payment. */
		new_payment.setOnClickListener {
			newPayment(user!!, userApi)
		}
	}

	protected fun User.listUserPayments(context: Context) {
		val payments : Array<Payment> = this.payments.toTypedArray()
		val adapter : ArrayAdapter<Payment> = ArrayAdapter(
				context,
				android.R.layout.simple_list_item_1,
				this.payments)
		list_payments.setAdapter(adapter)
	}

	protected fun newPayment(user: User, userApi: String) {
		intent = Intent(this@MainActivity, NewPaymentActivity::class.java)
		intent.putExtra("user.iban", user.iban)
		intent.putExtra("user.name", user.name)
		intent.putExtra("access_point", userApi)
		startActivity(intent)
	}
}
