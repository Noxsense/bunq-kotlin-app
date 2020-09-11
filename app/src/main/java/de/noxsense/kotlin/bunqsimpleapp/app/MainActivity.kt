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

	private val TAG = "Nox-Bunq-MainActivity"
	private var user : User? = null

	companion object {
		val confFile: String = "bunq-sandbox.conf"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		
		Log.i(TAG, "Started Application")

		try {
			/* android.os.NetworkOnMainThreadException
			 * and deprecated AsyncTask
			 * => kotlin coroutin. */
			runBlocking {
				launch(Dispatchers.Default) {
					Log.d(TAG, "call setupContext() ...")
					setupContext()
				}
			}

			/* list user payments. */
			Log.d(TAG, "call listUserPayments() ...")
			// listUserPayments()

			/* Option to make a new payment. */
			new_payment.setOnClickListener {
				Log.d(TAG, "Clicked $it - New Payment.")
				newPayment()
			}

		} catch (e : Exception) {
			Log.e(TAG, e.toString())
		}
	}

	protected fun newPayment() {
		/* Abort making a new payment. */
		try {
			Log.d(TAG, "Store API context before NewPaymentActivity is started.")
			var apiContext = restoreApiContext(confFile)
			apiContext.ensureSessionActive()
			BunqContext.loadApiContext(apiContext)

			Log.d(TAG, "start new activity")
			intent = Intent(this@MainActivity, NewPaymentActivity::class.java)
			// store again the BunqContext

			startActivity(intent)
		} catch (e: Exception) {
			toast("Cannot make a new payment because of ${e::class.simpleName}", Toast.LENGTH_LONG)
			Log.e(TAG, e.toString())
		}
	}

	protected fun listUserPayments() {
		val pagination = Pagination()
		pagination.setCount(10)

		Log.d(TAG, "Get all monetary banks")
		val moneytaryBanks = MonetaryAccountBank
			.list(pagination.getUrlParamsCountOnly())
			.getValue()

		Log.d(TAG, "Get all payments for the monetary banks.")
		val payments = moneytaryBanks
			.flatMap { Payment.list(it.getId(), pagination.getUrlParamsCountOnly()).getValue() }

		Log.d(TAG, "Set adapter to payments")
		val adapter = ArrayAdapter(
				this,
				android.R.layout.simple_list_item_1,
				payments)

		list_payments.setAdapter(adapter)

		Log.d(TAG, "Add OnItemClickListener")
		list_payments.setOnItemClickListener { _ , _, position, _ ->
			Log.d(TAG, "Showed Payment No. $position")
		}
	}

	fun setupContext() : ApiContext {
		Log.d(TAG, "Start Bunq SDK API")
		if (!File(confFile).exists()) {
			Log.d(TAG, "Create a new sandbox user with tinker.")

			// DOCS: https://public-api.sandbox.bunq.com/v1/
			var request = Request.Builder()
				.url("https://public-api.sandbox.bunq.com/v1/sandbox-user-person")
				.post(RequestBody.create(null, ByteArray(0)))
				.addHeader("Content-Type", "application/json" )
				.addHeader("Cache-Control", "none" )
				.addHeader("User-Agent", "curl-request" )
				.addHeader("X-Bunq-Client-Request-Id", "$(Date())randomId" )
				.addHeader("X-Bunq-Language", "nl_NL" )
				.addHeader("X-Bunq-Region", "nl_NL" )
				.addHeader("X-Bunq-Geolocation", "0 0 0 0 000")
				.build()

			Log.d(TAG, "Send request: $request")
			var response = OkHttpClient().newCall(request).execute()

			Log.d(TAG, "Received response: $response")
			if (response.code() == 200) {
				Log.d(TAG, "Response was OK, fetch API key and get SandboxUser")

				var apiKey = Gson().fromJson(
						response.body()!!.string(), JsonObject::class.java)
					.getAsJsonArray("Response")
					.get(0).getAsJsonObject()
					.get("ApiKey").getAsJsonObject()

				Log.d(TAG, "Get the SandboxUser")
				var sandboxuser = SandboxUser.fromJsonReader(
					JsonReader(StringReader(apiKey.toString())))

				Log.d(TAG, "Create a new ApiContext with API key")
				var apiContext = ApiContext
					.create(
						ApiEnvironmentType.SANDBOX,
						sandboxuser.getApiKey(),
						"Nox Bunq ?" /* device description */ )

					Log.d(TAG, "Store ApiContext.")
					saveApiContext(apiContext, confFile)

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
			}
		} else {
			Log.d(TAG, "File exists, load this.")
		}

		// restore
		Log.d(TAG, "Restore ApiContext from conf file.")
		var apiContext = restoreApiContext(confFile)
		apiContext.ensureSessionActive()
		BunqContext.loadApiContext(apiContext)

		user = User.get().getValue()

		return apiContext
	}

	protected fun toast(msg: Any?, duration: Int) {
		// Toast.makeText(getApplicationContext(), "${msg}", duration).show()
		description.text = "${msg}"
	}

	/** Override save(file). Shadowing with android friendly save and restore. */
	public fun saveApiContext(apiContext: ApiContext, filepath: String) {
		try {
			Log.d(TAG, "Open file $filepath")
			val file = File(getApplicationContext().filesDir, filepath)
			Log.d(TAG, "Write to $filepath: ${apiContext.toJson()}")
			FileUtils.writeStringToFile(file, apiContext.toJson(), "UTF-8")
		} catch (e: IOException) {
			Log.e(TAG, e.toString())
			throw BunqException("Could not save API Context", e)
		}
	}
	
	/** Override restore(file). Shadowing with android friendly save and restore. */
	public fun restoreApiContext(filepath: String) : ApiContext {
		try {
			val file = File(getApplicationContext().filesDir, filepath)
			val json = FileUtils.readFileToString(file, "UTF-8")
			return ApiContext.fromJson(json)
		} catch (e: IOException) {
		Log.e(TAG, e.toString())
			throw BunqException("Could not restore API context", e)
		}
	}
}
