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
import android.util.Log

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

import kotlinx.android.synthetic.main.activity_main.*

object BunqHandle {

	private val TAG = "Bunq-Handle"

	private val DEVICE_NOTE = "Nox' Lil' Bunq App (Android, 100% Kotlin)"

	private val WAIT_BTW_RQ = 3

	private val SANDBOX_STARTING_MONEY = Amount("500.00", "EUR")
	private val SUGAR_DADDY = Pointer("EMAIL", "sugardaddy@bunq.com")

	public val DEFAULT_CONF_FILE: String = "bunq-sandbox.conf"

	val LOGIN_FROM_FILE: String = ""
	val LOGIN_SANDBOX_NEW: String? = null

	fun postNewSandboxUser() : SandboxUser {
		Log.d(TAG, "Create a new sandbox user with tinker.")

		// DOCS: https://public-api.sandbox.bunq.com/v1/
		var request = Request.Builder()
			.url("https://" + ApiEnvironmentType.SANDBOX.getBaseUri() + "/v1/sandbox-user-person")
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
			return SandboxUser.fromJsonReader(
				JsonReader(StringReader(apiKey.toString())))
		}
		throw BunqException("Could not post a new SandboxUser.")
	}

	fun fetchPaymentsAndBalance() :
		Pair<List<Payment>, Map<MonetaryAccountBank, Amount>> {
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
		val payments = moneytaryBanks.flatMap {
				Payment
					.list(it.getId(), page.getUrlParamsCountOnly())
					// wait until the next request.
					.also { Thread.sleep(WAIT_BTW_RQ * 1000L) }
					.getValue()
			}

		Log.d(TAG, "Get all balances for the monetary banks.")
		return ((payments) to (moneytaryBanks.associateBy({it}, {it.getBalance()})))
	}

	/** Setup a new Bunq and API Context.
	 * If nothing is given or requested, just start a new Sandbox Session with
	 * with a new Sandbox User who pumps some money from Sugger Daddy.
	 * @param app the environemnt to store and load files.
	 * @param filepath the filename to store and load the save file.
	 * @param login Login Scenarios: Last Config (""), NewSandbox (null), Production API ("key")
	 * @return ApiContext() for the the current session.
	 */
	fun setupContextAt(app: Context, filepath: String = BunqHandle.DEFAULT_CONF_FILE, login: String? = LOGIN_FROM_FILE) : ApiContext {
		Log.d(TAG, "Setup the context stored to the file, or create a new one or overwrite with new login.")

		val fileExists = File(app.getFilesDir(), filepath).exists()

		/* Enforce new sandbox, or old settings were requested, but not existing. */
		if (login == LOGIN_SANDBOX_NEW || (login == LOGIN_FROM_FILE && !fileExists)) {
			Log.d(TAG, "Create a new sandbox user with tinker, ($login) and ($fileExists)")

			var sandboxuser = BunqHandle.postNewSandboxUser()
			// var sandboxuser = SandboxUser.create()

			Log.d(TAG, "Create a new ApiContext with API key")
			var sandboxContext = ApiContext
				.create(
					ApiEnvironmentType.SANDBOX,
					sandboxuser.getApiKey(),
					DEVICE_NOTE)

			Log.d(TAG, "Store ApiContext.")
			storeContext(app, filepath, sandboxContext)

			BunqContext.loadApiContext(sandboxContext)

			Log.d(TAG, "Get money from sugar daddy.")
			/* As sandbox user, we pump money from our sugardaddy. */
			RequestInquiry.create(
				SANDBOX_STARTING_MONEY,
				SUGAR_DADDY,
				"You're the best!",
				false /* allow bunqme */)

			storeContext(app, filepath, BunqContext.getApiContext())
		} else if (login != LOGIN_SANDBOX_NEW && login != LOGIN_FROM_FILE) {
			Log.i(TAG, "Login with a given API key and in PRODUCTION")

			var loginContext = ApiContext.create(
				ApiEnvironmentType.PRODUCTION,
				login,
				DEVICE_NOTE)

			Log.d(TAG, "Store/Overwrite ApiContext.")
			storeContext(app, filepath, loginContext)

		}

		// Restore from file (maybe just set)
		Log.d(TAG, "Restore ApiContext from conf file.")
		var apiContext = restoreContext(app, filepath)
		apiContext.ensureSessionActive()
		BunqContext.loadApiContext(apiContext)

		return apiContext
	}

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

	/** Android friendly version to save the configuration file
	 * @param context the environemnt to store and load files.
	 * @param filepath the filename to store and load the save file.
	 * @param apiContext the ApiContext to Store.
	 */
	public fun storeContext(
		context: Context,
		filepath: String = BunqHandle.DEFAULT_CONF_FILE,
		apiContext: ApiContext = BunqContext.getApiContext()
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
		filepath: String = BunqHandle.DEFAULT_CONF_FILE
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
