package de.noxsense.kotlin.bunqsimpleapp.app

import com.bunq.sdk.context.ApiContext
import com.bunq.sdk.context.ApiEnvironmentType
import com.bunq.sdk.context.BunqContext
import com.bunq.sdk.exception.BunqException
import com.bunq.sdk.http.Pagination
import com.bunq.sdk.model.generated.`object`.Amount
import com.bunq.sdk.model.generated.`object`.Pointer
import com.bunq.sdk.model.generated.endpoint.MonetaryAccountBank
import com.bunq.sdk.model.generated.endpoint.Payment
import com.bunq.sdk.model.generated.endpoint.RequestInquiry
import com.bunq.sdk.model.generated.endpoint.SandboxUser
import com.bunq.sdk.model.generated.endpoint.User

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.IOException
import java.io.StringReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.commons.io.FileUtils

import android.content.Context
import android.database.DataSetObserver
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.Toast
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.*

import kotlinx.android.synthetic.main.activity_main.*
// import kotlinx.android.synthetic.main.list_item_payment.*

class MainActivity : AppCompatActivity() {

	private val TAG = "Bunq-Main"

	private var user: User? = null
	private var payments: List<Payment> = listOf()
	private var balances: Map<MonetaryAccountBank, Amount> = mapOf()

	// get first money as sandbox user.
	private val SANDBOX_STARTING_MONEY = Amount("500.00", "EUR")
	private val SUGAR_DADDY = Pointer("EMAIL", "sugardaddy@bunq.com")

	companion object {
		/** Override save(file). Shadowing with android friendly save and restore. */
		public fun storeContext(
			context: Context,
			apiContext: ApiContext = BunqContext.getApiContext(),
			filepath: String = BunqHandle.confFile
		) {
			try {
				Log.d("Bunq-Companion", "Open file $filepath")
				val file = File(context.filesDir, filepath)
				Log.d("Bunq-Companion", "Write to $filepath: ${apiContext.toJson()}")
				FileUtils.writeStringToFile(file, apiContext.toJson(), "UTF-8")
			} catch (e: IOException) {
				Log.e("Bunq-Companion", e.toString())
				throw BunqException("Could not save API Context", e)
			}
		}

		/** Override restore(file). Shadowing with android friendly save and restore. */
		public fun restoreContext(
			context: Context,
			filepath: String = BunqHandle.confFile
		) : ApiContext {
			try {
				val file = File(context.filesDir, filepath)
				val json = FileUtils.readFileToString(file, "UTF-8")
				return ApiContext.fromJson(json)
			} catch (e: IOException) {
				Log.e("Bunq-Companion", e.toString())
				throw BunqException("Could not restore API context", e)
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		Log.i(TAG, "Started Application")

		try {
			/* Option to make a new payment. */
			new_payment_button.setOnClickListener {
				Log.d(TAG, "Clicked $it - New Payment.")
				newPayment()
			}

			/* List user payments. */
			Log.d(TAG, "call setupPaymentAdapter() ...")
			setupPaymentAdapter()
			Log.d(TAG, "Adapter is set up.")

			greeting.text = getResources().getString(R.string.greeting)
				.format(user?.getUserPerson()?.getDisplayName() ?: "User")

			// refresh "button" => Simulate onResume() = load payments and balances.
			list_payments_refresh.setOnClickListener {
				Log.i(TAG, "Refresh clicked!")
				list_payments.setAlpha(0.02.toFloat())
				onResume()
			}
		} catch (e : Exception) {
			Log.e(TAG, e.toString())
		}
	}

	override fun onResume() {
		super.onResume()

		try {
			// restore (or create) context.
			runBlocking {
				launch(Dispatchers.Default) {
					Log.d(TAG, "call setupContextAt() ...")
					setupContextAt()
				}
			}

			// reload payments.
			Log.d(TAG, "Resumed: Fetch payments again.")
			reloadPayments()
			Log.i(TAG, "Payments reloaded, displayed: ${list_payments.adapter.count}.")

			Log.i(TAG, "Display balances: ${balances}")
			balances_view.text = getResources().getString(R.string.balances_view)
				.format(balances.toList().joinToString("", "\n") { (a, b) ->
					// "$circ account(alias): balance(value/currency)"
					"\u2218 ${b.show()} in ${a.show()}\n"
				})

			list_payments.setAlpha(0.6.toFloat())
		} catch (e: Exception) {
			Log.e(TAG, "Resuming with Exception")
			Log.e(TAG, e.toString())
		}
	}

	private fun MonetaryAccountBank.show() : String
		= "${this.getDisplayName()} (${this.getStatus()})"

	private fun Amount.show() : String
		= "${this.getCurrency()} ${this.getValue()}"

	override fun onPause() {
		super.onPause()
		runBlocking {
			launch(Dispatchers.Default) {
				Log.d(TAG, "Pausing, store context.")
				val apiContext = BunqContext.getApiContext()
				storeContext(this@MainActivity, apiContext)
				apiContext.ensureSessionActive()
				BunqContext.loadApiContext(apiContext)
			}
		}
	}

	protected fun toast(msg: Any?, duration: Int) {
		Toast.makeText(getApplicationContext(), "${msg}", duration).show()
	}

	protected fun newPayment() {
		/* Abort making a new payment. */
		try {
			// Log.d(TAG, "Store API context before NewPaymentActivity is started.")

			Log.d(TAG, "start new activity")
			intent = Intent(this@MainActivity, NewPaymentActivity::class.java)
			startActivity(intent)
		} catch (e: Exception) {
			toast("Cannot make a new payment because of ${e::class.simpleName}", Toast.LENGTH_LONG)
			Log.e(TAG, e.toString())
		}
	}

	private fun reloadPayments() {
		runBlocking {
			launch(Dispatchers.Default) {
				Log.d(TAG, "Creata a new Pagination")
				val page = Pagination()
				page.setCount(10)

				Log.d(TAG, "Get all monetary banks")
				val moneytaryBanks = MonetaryAccountBank
					.list(page.getUrlParamsCountOnly())
					.getValue()

				Log.d(TAG, "Counted ${moneytaryBanks.size}, see: ${moneytaryBanks}")

				Log.d(TAG, "Get all payments for the monetary banks.")
				payments = moneytaryBanks.flatMap {
						Payment.list(it.getId(), page.getUrlParamsCountOnly())
							.getValue()
					}

				Log.d(TAG, "Get all balances for the monetary banks.")
				balances = moneytaryBanks.associateBy( {it}, {it.getBalance()})
			}
		}
	}

	protected fun setupPaymentAdapter() {
		Log.d(TAG, "Counted ${payments.size}, see: ${payments}")

		// initiate payemts before.
		// reloadPayments()

		Log.d(TAG, "Set adapter with custom view")
		try {
			list_payments.adapter = object: ListAdapter {
				override fun areAllItemsEnabled() : Boolean = true

				override fun getCount() : Int = payments.size

				override fun isEmpty() : Boolean = getCount() < 1

				override fun getItem(p0: Int) : Payment = payments.get(p0)

				override fun isEnabled(p0: Int) : Boolean = true

				override fun getItemId(p0: Int) : Long = p0.toLong()

				override fun hasStableIds() : Boolean = true

				override fun getItemViewType(p0: Int) : Int = -1

				override fun getViewTypeCount() : Int = getCount()

				override fun getView(p0: Int, view: View?, parent: ViewGroup) : View {
					Log.d(TAG, "View Payment ${p0 + 1} / ${getCount()}")
					return paymentToView(parent, this.getItem(p0))
				}

				override fun registerDataSetObserver(observer: DataSetObserver) {}
				override fun unregisterDataSetObserver(observer: DataSetObserver) {}
			}
		} catch (e: IllegalArgumentException) {
			Log.w(TAG, "Caught $e on setting up the Payment Adapter.")
		}

		Log.d(TAG, "Add OnItemClickListener")
		list_payments.setOnItemClickListener { _ , view, _, _ ->
			val expandable: View
				= view.findViewById(R.id.list_payment_expanded)

			expandable.visibility = when (expandable.visibility) {
				View.GONE -> View.VISIBLE
				else -> View.GONE
			}
		}
	}

	/** Payment-Item (expandable on click).
	 * +================================+
	 * |[+-] XX.xx EUR | to/from | when |
	 * +--------------------------------+
	 * |Type (sub)|             Account |
	 * +--------------------------------+
	 * |Description                     |
	 * +================================+ */
	private fun paymentToView(parent: ViewGroup, payment: Payment) : View {
		Log.d(TAG, "Initate Custom View.")
		val li = LayoutInflater.from(this)
		val item = li.inflate(R.layout.list_item_payment, parent, false)

		val amount = payment.getAmount()
		val amountValue = amount.getValue().toDouble()
		val paid = amountValue < 0

		val counter = payment.getCounterpartyAlias().getDisplayName()

		item.setBackground(getDrawable(when {
			paid -> R.drawable.roundly_bordered_bunq_c09 // dark red
			else -> R.drawable.roundly_bordered_bunq_c01 // dark green
		})).also {
			Log.d(TAG, "Coloured the paymen list item accordingly to the value ${paid} \u21d2 ${item.getBackground()}.")
		}

		(item.findViewById(R.id.list_payment_amount) as TextView)
			.text = amount.run {
				"%+8.2f %s".format(amountValue, this.getCurrency())
			}

		(item.findViewById(R.id.list_payment_counteralias) as TextView)
			.text = "${if (paid) "to" else "from"} $counter"

		(item.findViewById(R.id.list_payment_date) as TextView)
			.text = payment.getUpdated().substring(0,16) // crop seconds.

		(item.findViewById(R.id.list_payment_type) as TextView)
			.text = "${payment.getType()} (${payment.getSubType()})"

		(item.findViewById(R.id.list_payment_description) as TextView)
			.text =  payment.getDescription()

		Log.d(TAG, "Displaying Payment ${payment.getId()}.")

		return item
	}

	fun setupContextAt(filepath: String = BunqHandle.confFile) : ApiContext {
		Log.d(TAG, "setup the context stored to the file, or create a new one.")
		if (!File(this.getFilesDir(), filepath).exists()) {
			Log.d(TAG, "Create a new sandbox user with tinker.")

			var sandboxuser = BunqHandle.postNewSandboxUser()
			// var sandboxuser = SandboxUser.create()

			Log.d(TAG, "Create a new ApiContext with API key")
			var apiContext = ApiContext
				.create(
					ApiEnvironmentType.SANDBOX,
					sandboxuser.getApiKey(),
					"Nox Bunq ?" /* device description */ )

				Log.d(TAG, "Store ApiContext.")
				storeContext(this@MainActivity, apiContext, filepath)

			BunqContext.loadApiContext(apiContext)

			Log.d(TAG, "Get money from sugar daddy.")
			/* As sandbox user, we pump money from our sugardaddy. */
			RequestInquiry.create(
				SANDBOX_STARTING_MONEY,
				SUGAR_DADDY,
				"You're the best!",
				false /* allow bunqme */)

			storeContext(this@MainActivity, BunqContext.getApiContext(), filepath)

		} else {
			Log.d(TAG, "File exists, load this.")
		}

		// restore
		Log.d(TAG, "Restore ApiContext from conf file.")
		var apiContext = restoreContext(this, filepath)
		apiContext.ensureSessionActive()
		BunqContext.loadApiContext(apiContext)

		user = User.get().getValue()

		return apiContext
	}
}
