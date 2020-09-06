package de.noxsense.kotlin.bunqsimpleapp.app

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
// import de.noxsense.kotlin.bunqsimpleapp.library.FactorialCalculator
import de.noxsense.kotlin.bunqsimpleapp.library.android.NotificationUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

	private val notificationUtil: NotificationUtil by lazy { NotificationUtil(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		confirm.setOnClickListener {
			/* Compute Input. */
			// val input = edit_text_factorial.text.toString().toInt()

			/* Make Extra window visible. */
			// text_result.text = result
			// text_result.visibility = View.VISIBLE

			notificationUtil.showNotification(
				context = this,
				title = "Notification title",
				message = "Notification Content"
				// title = getString(R.string.notification_title),
				// message = result
			)
		}

		val array = arrayOf("Payment 3", "Payment 2",  "Payment 1",   "Payment 0")

		val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, array)

		list_payments.setAdapter(adapter)
	}
}
