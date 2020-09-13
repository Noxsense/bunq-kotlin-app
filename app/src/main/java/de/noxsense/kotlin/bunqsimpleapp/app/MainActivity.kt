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
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.*

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

	private val TAG = "Bunq-Main"
	private var payments: List<Payment> = listOf()

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
			/* list user payments. */
			Log.d(TAG, "call setupPaymentAdapter()() ...")
			setupPaymentAdapter()

			/* Option to make a new payment. */
			new_payment.setOnClickListener {
				Log.d(TAG, "Clicked $it - New Payment.")
				newPayment()
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
			with (list_payments.adapter as ArrayAdapter<Payment>) {
				this.clear()
				this.addAll(payments)
				this.notifyDataSetChanged()
			}
			Log.i(TAG, "Payments reloaded, displayed: ${list_payments.adapter.count}.")
		} catch (e: Exception) {
			Log.e(TAG, "Resuming with Exception")
			Log.e(TAG, e.toString())
		}
	}

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
			}
		}
	}

	private fun showPayment(position: Int) : Payment
		= payments.get(position).also {
			Log.d(TAG, "Show Payment, id $position/${payments.size}: $it")
			// it.getBalanceAfterMutation()
			Log.i(TAG, "Amount: ${it.getAmount().getCurrency()} ${it.getAmount().getValue()}")
			Log.i(TAG, "Date:   ${it.getCreated()}")
			Log.i(TAG, "From:   ${it.getAlias().getDisplayName()}")
			Log.i(TAG, "To:     ${it.getCounterpartyAlias().getDisplayName()}")
			Log.i(TAG, "Note:   ${it.getDescription()}")
			Log.i(TAG, "Type:   ${it.getType()} (${it.getSubType()})")
		}

	protected fun setupPaymentAdapter() {
		Log.d(TAG, "Counted ${payments.size}, see: ${payments}")

		// initiate payemts before.
		// reloadPayments()

		Log.d(TAG, "Set adapter to payments")
		list_payments.adapter = ArrayAdapter<Payment>(this, android.R.layout.simple_list_item_1)
		(list_payments.adapter as ArrayAdapter<*>).setNotifyOnChange(true)

		Log.d(TAG, "Add OnItemClickListener")
		list_payments.setOnItemClickListener { _ , _, position, _ ->
			showPayment(position)
		}
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

			// psd2 context.
			/*
			var apiContext = ApiContext.createForPsd2(
				ENVIRONMENT_TYPE,
				SecurityUtils.getCertificateFromFile(PATH_TO_CERTIFICATE),
				SecurityUtils.getPrivateKeyFromFile(PATH_TO_PRIVATE_KEY),
				new Certificate[]{
						SecurityUtils.getCertificateFromFile(PATH_TO_CERTIFICATE_CHAIN)
				},
				DESCRIPTION
			) */

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

		return apiContext
	}
}
