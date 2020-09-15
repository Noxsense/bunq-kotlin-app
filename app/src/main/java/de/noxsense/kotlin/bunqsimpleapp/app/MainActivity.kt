package de.noxsense.kotlin.bunqsimpleapp.app

import com.bunq.sdk.context.BunqContext
import com.bunq.sdk.exception.BadRequestException
import com.bunq.sdk.exception.BunqException
import com.bunq.sdk.model.generated.`object`.Amount
import com.bunq.sdk.model.generated.endpoint.MonetaryAccountBank
import com.bunq.sdk.model.generated.endpoint.Payment
import com.bunq.sdk.model.generated.endpoint.User

import android.app.AlertDialog
import android.app.AlertDialog.Builder
import android.content.Context
import android.view.View.OnClickListener
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), OnClickListener {

	val TAG = "Bunq-Main"

	private var user: User? = null
	private val payments: MutableList<Payment> = mutableListOf()
	private val balances: MutableMap<MonetaryAccountBank, Amount> = mutableMapOf()

	/* the login method.
	 * empty => uses an exisiting, saved api key.
	 * null => creates a new (sandbox) api key
	 * key => login into production */
	private var login: String? = ""

	private var dialogLogin: AlertDialog? = null
	private var btnLoginExisiting: TextView? = null
	private var btnLoginSandboxNew: TextView? = null
	private var btnLoginProduction: TextView? = null
	private var editLoginProduction: EditText? = null

	private var btnRefreshList: TextView? = null
	private var btnChangeContext: TextView? = null
	private var btnMakeNewPayemnt: TextView? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		Log.i(TAG, "Started Application")

		try {
			/* Option to make a new payment. */
			btnMakeNewPayemnt = (new_payment_button as TextView)
				.also { it.setOnClickListener(this) }

			/* Create option to use other login. */
			btnChangeContext = (change as TextView)
				.also { it.setOnClickListener(this) }


			/* refresh "button" => Simulate onResume() = load payments and balances.
			 * no bunq call to set this up. */
			btnRefreshList = list_payments_refresh
				.also { it.setOnClickListener(this) }

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

	private fun askForApiLogin() {
		val builder = AlertDialog.Builder(this)
		val infl = LayoutInflater.from(this)

		val view = infl.inflate(R.layout.dialog_api_question, null)

		// set up the buttons.
		btnLoginExisiting = view.findViewById(R.id.use_existing)
		btnLoginSandboxNew = view.findViewById(R.id.use_new_sandbox)
		btnLoginProduction = view.findViewById(R.id.use_production)
		editLoginProduction = view.findViewById(R.id.api_key)

		btnLoginExisiting!!.setOnClickListener(this)
		btnLoginSandboxNew!!.setOnClickListener(this)
		btnLoginProduction!!.setOnClickListener(this)

		builder.setView(view)
		builder.setCancelable(true)
		builder.setOnDismissListener{
			Log.i(TAG, "API login done: Login is '${login}'")
			try {
				fetchBung(login)
			} catch (e: Exception) {
				fetchBung("") // stay with old things.
				showToast(e.toString())
			}
			btnLoginExisiting!!.visibility = View.VISIBLE
			btnLoginSandboxNew!!.visibility = View.VISIBLE
			editLoginProduction!!.visibility = View.GONE
		}
		val dialog = builder.create() // returns dialog.

		dialog.run {
			dialogLogin = this // referencing to val.
			getWindow()?.setBackgroundDrawableResource(
				R.drawable.dialog_api_question_bg)
			dialog.show()
		}
	}

	override fun onResume() {
		super.onResume()
		try {
			fetchBung()
		} catch (e: Error) {
			Log.e(TAG, "Error occurred")
			Log.e(TAG, e.toString())
			Log.e(TAG, e.getStackTrace().toString())
		} catch (e: Exception) {
			Log.e(TAG, "Resuming with Exception")
			Log.e(TAG, e.toString())
		}
	}

	/** Make a quick Toast Note. */
	protected fun showToast(msg: Any?, duration: Int = Toast.LENGTH_LONG) {
		Toast.makeText(this.getApplicationContext(), "${msg}", duration).show()
	}

	override fun onClick(view: View) {
		when (view) {
			btnLoginExisiting -> {
				Log.i(TAG, "Load existing.")
				login = BunqHandle.LOGIN_FROM_FILE
				dialogLogin!!.dismiss()
			}

			btnLoginSandboxNew -> {
				Log.i(TAG, "Create new Sandbox.")
				login = BunqHandle.LOGIN_SANDBOX_NEW
				dialogLogin!!.dismiss()
			}

			btnLoginProduction -> {
				Log.i(TAG, "Use API key from Production.")
				if (editLoginProduction!!.visibility != View.VISIBLE) {
					btnLoginExisiting!!.visibility = View.GONE
					btnLoginSandboxNew!!.visibility = View.GONE
					editLoginProduction!!.visibility = View.VISIBLE
				} else {
					login = editLoginProduction!!.text.toString().trim()
					dialogLogin!!.dismiss()
				}
			}

			btnChangeContext -> {
				if (dialogLogin == null) {
					askForApiLogin()
				} else {
					dialogLogin!!.show()
				}
			}

			btnMakeNewPayemnt -> {
				if (BunqHandle.isOnline()) {
					Log.d(TAG, "Clicked 'New Payment'.")
					newPayment()
				} else {
					Log.d(TAG, "Clicked 'New Payment', but offline.")
					showToast("You are offline.", Toast.LENGTH_SHORT)
				}
			}

			btnRefreshList -> {
				if (BunqHandle.isOnline()) {
					Log.i(TAG, "Clicked 'Refresh'.")
					list_payments_refresh.setAlpha(0.02.toFloat())
					showToast("Refreshing!")
					onResume()
				} else {
					Log.d(TAG, "Clicked 'Refresh', but offline.")
					showToast("You are offline.", Toast.LENGTH_SHORT)
				}
			}
		}
	}

	private fun fetchBung(method: String? = BunqHandle.LOGIN_FROM_FILE) {
		list_payments_refresh.setAlpha((0.7).toFloat())

		// restore (or create) context.
		runBlocking {
			launch(Dispatchers.Default) {
				Log.d(TAG, "Restore (or Create Context) -> Call setupContextAt()")
				BunqHandle.setupContextAt(this@MainActivity, BunqHandle.DEFAULT_CONF_FILE, method)
				user = User.get().getValue()

				// receveived payemnts and balance overviews.
				Log.d(TAG, "Fetch Payments and Balances.")
				val (rpayments, rbalances) = BunqHandle.fetchPaymentsAndBalance()

				// try not to change the pointer.
				payments.clear()
				payments += rpayments

				balances.clear()
				balances += rbalances
			}
		}

		// reload name.
		greeting.text = getResources().getString(R.string.greeting)
			.format(user?.getUserPerson()?.getDisplayName() ?: "User")

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
		login = BunqHandle.LOGIN_FROM_FILE // reset to default.
	}

	private fun MonetaryAccountBank.show() : String
		= "${this.getDisplayName()} (${this.getStatus()})"

	private fun Amount.show() : String
		= "${this.getCurrency()} ${this.getValue()}"

	override fun onPause() {
		super.onPause()
		storeBunqLocally()
	}

	private fun storeBunqLocally() {
		runBlocking {
			launch(Dispatchers.Default) {
				try {
					Log.d(TAG, "Pausing, store context.")
					BunqContext.getApiContext().run {
						BunqHandle.storeContext(context = this@MainActivity, apiContext = this)
						this.ensureSessionActive()
						BunqContext.updateApiContext(this)
					}
				} catch (e: BunqException) {
					Log.e(TAG, "Could not store the Bunq Context.")
					Log.e(TAG, e.toString())
				} catch (e: Exception) {
					Log.e(TAG, "Something (else) went wrong when pausing.")
					Log.e(TAG, e.toString())
				}
			}
		}
	}

	/** Change the view to make a new Payment.
	 * Start a new activity NewPaymentActivity.
	 * If the device is offline, or no session is active,
	 * this step might be prohibited.
	 */
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

	protected fun setupPaymentAdapter() {
		Log.d(TAG, "Counted payments: ${payments.size}, see: ${payments}")

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
	 * +==================================+
	 * |[+-] XX.xx EUR | to/from | update |
	 * +----------------------------------+
	 * |Type (sub)|              ?Account |
	 * +----------------------------------+
	 * |Description                       |
	 * +==================================+ */
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
}
