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
import java.net.InetAddress
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.commons.io.FileUtils

import android.content.Context
import android.content.Intent
import android.database.DataSetObserver
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.*

import kotlinx.android.synthetic.main.activity_main.*
// import kotlinx.android.synthetic.main.list_item_payment.*

class MainActivity : AppCompatActivity() {

	private var user: User? = null
	private val payments: MutableList<Payment> = mutableListOf()
	private val balances: MutableMap<MonetaryAccountBank, Amount> = mutableMapOf()

	val WAIT_BTW_RQ = 3

	// get first money as sandbox user.
	private val SANDBOX_STARTING_MONEY = Amount("500.00", "EUR")
	private val SUGAR_DADDY = Pointer("EMAIL", "sugardaddy@bunq.com")

	companion object {
		val TAG = "Bunq-Main"

		/** Check for connectivity. */
		public fun isOnline() : Boolean {
			var online : Boolean = false
			runBlocking { launch(Dispatchers.Default) {
				online = try {
					!InetAddress.getByName("bunq.com").equals("")
				} catch (e: Exception) {
					false . also {
						Log.i("Bunq-Main", "Tested Connectivity, offline cause: $e")
					}
				}
			}}
			return online
		}

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
				if (isOnline()) {
					Log.d(TAG, "Clicked 'New Payment'.")
					newPayment()
				} else {
					Log.d(TAG, "Clicked 'New Payment', but offline.")
					showToast("You are offline.", Toast.LENGTH_SHORT)
				}
			}

			/* refresh "button" => Simulate onResume() = load payments and balances.
			 * no bunq call to set this up. */
			list_payments_refresh.setOnClickListener {
				if (isOnline()) {
					Log.i(TAG, "Clicked 'Refresh'.")
					list_payments_refresh.setAlpha(0.02.toFloat())
					showToast("Refreshing!")
					onResume()
				} else {
					Log.d(TAG, "Clicked 'Refresh', but offline.")
					showToast("You are offline.", Toast.LENGTH_SHORT)
				}
			}

			/* List user payments.
			 * no bunq call to set this up.  */
			Log.d(TAG, "call setupPaymentAdapter() ...")
			setupPaymentAdapter()
			Log.d(TAG, "Adapter is set up.")

			/* Show User's DisplayName.
			 * Needs|Makes a bunq call. */
			greeting.text = getResources().getString(R.string.greeting)
				.format(user?.getUserPerson()?.getDisplayName() ?: "User")

			balances_view.text = getResources().getString(R.string.balances_view)
				.format("... yet unknown ...")
		} catch (e : Exception) {
			Log.e(TAG, e.toString())
		}
	}

	override fun onResume() {
		super.onResume()
		try {
			list_payments_refresh.setAlpha((0.7).toFloat())

			// restore (or create) context.
			runBlocking {
				launch(Dispatchers.Default) {
					Log.d(TAG, "Restore (or Create Context) -> Call setupContextAt()")
					setupContextAt()
				}
			}

			// reload name.
			greeting.text = getResources().getString(R.string.greeting)
				.format(user?.getUserPerson()?.getDisplayName() ?: "User")

			// reload payments.
			Log.d(TAG, "Resumed: Fetch payments again.")
			reloadPayments()

			// show balances
			Log.i(TAG, "Display balances: ${balances.values}")
			balances_view.text = getResources().getString(R.string.balances_view)
				.format(balances.toList().joinToString("", "\n") { (a, b) ->
					// "$circ account(alias): balance(value/currency)"
					"\u2218 ${b.show()} in ${a.show()}\n"
				})

			// list payments.
			Log.i(TAG, "Payments reloaded ${payments.size}, displayed: ${list_payments.adapter.count}.")
			(list_payments.adapter as ArrayAdapter<Payment>).run {
				this.notifyDataSetChanged()
			}
		} catch (e: Error) {
			Log.e(TAG, "Error occurred")
			Log.e(TAG, e.toString())
			Log.e(TAG, e.getStackTrace().toString())
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
				try {
					Log.d(TAG, "Pausing, store context.")
					val apiContext = BunqContext.getApiContext()
					storeContext(this@MainActivity, apiContext)
					apiContext.ensureSessionActive()
					BunqContext.updateApiContext(apiContext)
				} catch (e: BunqException) {
					Log.e(TAG, "Could not store the Bunq Context.")
					Log.e(TAG, e.toString())
					Log.e(TAG, e.getStackTrace().toString())
				} catch (e: Exception) {
					Log.e(TAG, "Something (else) went wrong when pausing.")
					Log.e(TAG, e.toString())
					Log.e(TAG, e.getStackTrace().toString())
				}
			}
		}
	}

	protected fun showToast(msg: Any?, duration: Int = Toast.LENGTH_LONG) {
		Toast.makeText(this.getApplicationContext(), "${msg}", duration).show()
	}

	protected fun newPayment() {
		/* Abort making a new payment. */
		try {
			/* Only be able to make a new payment, if the session is active. */
			if (BunqContext.getApiContext().isSessionActive()) {
				// provoke to test, if the API context is set.
				Log.d(TAG, "start new activity")
				intent = Intent(this@MainActivity, NewPaymentActivity::class.java)
				startActivity(intent)
			} else {
				showToast("No Session active! You cannot make a new payment now.")
			}
		} catch (e: Exception) {
			showToast("Cannot make a new payment because of ${e::class.simpleName}")
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

				Log.d(TAG, "Counted banks: ${moneytaryBanks.size}, see: ${moneytaryBanks}")

				// wait until the next request.
				Log.i(TAG, "Wait $WAIT_BTW_RQ seconds")
				Thread.sleep(WAIT_BTW_RQ * 1000L)

				Log.d(TAG, "Get all payments for the monetary banks.")
				val fetched = moneytaryBanks.flatMap {
						Payment
							.list(it.getId(), page.getUrlParamsCountOnly())
							// wait until the next request.
							.also { Thread.sleep(WAIT_BTW_RQ * 1000L) }
							.getValue()
					}

				// try not to change the pointer.
				payments.clear()
				payments += fetched

				Log.d(TAG, "Get all balances for the monetary banks.")
				balances.clear()
				balances += moneytaryBanks.associateBy( {it}, {it.getBalance()})
			}
		}
	}

	protected fun setupPaymentAdapter() {
		Log.d(TAG, "Counted payments: ${payments.size}, see: ${payments}")

		// initiate payemts before.
		// reloadPayments()

		Log.d(TAG, "Set adapter with custom view")
		try {
			list_payments.adapter = object: ArrayAdapter<Payment>(
				this, R.layout.list_item_payment, this@MainActivity.payments
			) {
				override fun getView(p0: Int, view: View?, parent: ViewGroup) : View {
					Log.d(TAG, "View Payment ${p0 + 1} / ${getCount()}")
					return paymentToView(parent, this.getItem(p0))
				}
			}
		} catch (e: IllegalArgumentException) {
			Log.w(TAG, "Caught $e on setting up the Payment Adapter.")
		}

		Log.d(TAG, "Add OnItemClickListener")
		list_payments.setOnItemClickListener { _ , view, pos, _ ->
			Log.d(TAG, "Clicked position $pos, on $view: ${payments.get(pos)}")

			val expandable: View
				= view.findViewById(R.id.list_payment_expanded)

			expandable.visibility = when (expandable.visibility) {
				View.GONE -> View.VISIBLE.also { Log.d(TAG, "Item[$pos] now displayed.") }
				else -> View.GONE.also { Log.d(TAG, "Item[$pos] now hidden.") }
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
	private fun paymentToView(parent: ViewGroup, payment: Payment?) : View {

		Log.d(TAG, "Initate Custom View.")
		val li = LayoutInflater.from(this)
		val item = li.inflate(R.layout.list_item_payment, parent, false)

		if (payment == null) return item

		val amount = payment.getAmount()
		val amountValue = amount.getValue().toDouble()
		val paid = amountValue < 0

		val counter = payment.getCounterpartyAlias().getDisplayName()

		item.setBackground(getDrawable(when {
			paid -> R.drawable.roundly_bordered_bunq_payment // dark red
			else -> R.drawable.roundly_bordered_bunq_request // dark green
		})).also {
			Log.d(TAG, "Coloured the payment list item accordingly "
				+ "to the value ${paid} \u21d2 ${item.getBackground()}.")
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
