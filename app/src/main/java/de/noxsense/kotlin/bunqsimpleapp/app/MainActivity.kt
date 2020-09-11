package de.noxsense.kotlin.bunqsimpleapp.app

import com.bunq.sdk.context.ApiContext
import com.bunq.sdk.context.ApiEnvironmentType
import com.bunq.sdk.context.BunqContext
// import com.bunq.sdk.exception.BunqException
import com.bunq.sdk.http.Pagination
import com.bunq.sdk.model.generated.endpoint.Payment
import com.bunq.sdk.model.generated.endpoint.User
import com.bunq.sdk.model.generated.endpoint.MonetaryAccountBank
import com.bunq.sdk.model.generated.endpoint.SandboxUser
import com.bunq.sdk.model.generated.`object`.Amount
import com.bunq.sdk.model.generated.`object`.Pointer
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader

import java.io.File
import java.io.StringReader

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import kotlinx.coroutines.*

import de.noxsense.kotlin.bunqsimpleapp.library.android.NotificationUtil

import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

	private val notificationUtil: NotificationUtil by lazy { NotificationUtil(this) }

	private var user : User? = null

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

		try {
			// android.os.NetworkOnMainThreadException
			// and deprecated AsyncTask
			// => kotlin coroutin.
			runBlocking {
				launch(Dispatchers.Default) {
					setupContext()
				}
			}

			// list user payments.
			listUserPayments()

		} catch (e : Exception) {
			Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
		}


		/* Option to make a new payment. */
		new_payment.setOnClickListener {

			// newPayment(user!!, userApi)
			try {

				// tryout payment creation.
				Payment.create(
					Amount("0.01", "EUR"),
					Pointer("EMAIL", "noxsense@gmail.com"),
					"Test Payment from Nox' Lil' Bunq App"
					)
			} catch (e : Exception) {
				Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
			}
		}
	}

	protected fun listUserPayments() {
		val pagination = Pagination()
		pagination.setCount(10)

		val moneytaryBanks = MonetaryAccountBank
			.list(pagination.getUrlParamsCountOnly())
			.getValue()

		val payments = moneytaryBanks
			.flatMap { Payment.list(it.getId(), pagination.getUrlParamsCountOnly()).getValue() }

		val adapter = ArrayAdapter(
				this,
				android.R.layout.simple_list_item_1,
				payments)

		list_payments.setAdapter(adapter)
		list_payments.setOnItemClickListener { _ , view, position, id ->
			Toast.makeText(
					this,
					"Show payment ${payments[position]}",
					Toast.LENGTH_LONG
				).show()
		}
	}

	fun setupContext() : ApiContext {
		val confFile = "bunq-sandbox.conf"

		if (File(confFile).exists()) {
		} else {
			var bytes : ByteArray = ByteArray(0)

			// this will generate a new sandbox user!
			var request = Request.Builder()
				.url("https://sandbox.public.api.bunq.com/v1/sandbox-user")
					// TODO (2020-09-11) SSL no alternative ertificate subject name matches target host name.
				.post(RequestBody.create(null, bytes))
				.addHeader("x-bunq-request-id", "1234")
				.addHeader("cache-control", "no-cache")
				.addHeader("x-bunq-geolocation", "0 0 0 0 NL")
				.addHeader("x-bunq-language", "en_US")
				.addHeader("x-bunq-region", "en_US")
				.build()

			var response = OkHttpClient().newCall(request).execute();

			if (response.code() == 200) {
				var responseString = response.body()!!.string()
				var jsonObject = Gson().fromJson(responseString, JsonObject::class.java)

				var apiKey = jsonObject
					.getAsJsonArray("Response")
					.get(0).getAsJsonObject()
					.get("ApiKey").getAsJsonObject()

				var sandboxuser = SandboxUser.fromJsonReader(
					JsonReader(StringReader(apiKey.toString())))

				var apiContext = ApiContext
					.create(
						ApiEnvironmentType.SANDBOX,
						sandboxuser.getApiKey(),
						"Nox Bunq ?" /* device description */ )
					.save(confFile)

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
		}

		// restore
		var apiContext = ApiContext.restore(confFile)
		apiContext.ensureSessionActive()
		BunqContext.loadApiContext(apiContext)

		user = User.get().getValue()

		return apiContext
	}

	protected fun newPayment(user: User, userApi: String) {
		intent = Intent(this@MainActivity, NewPaymentActivity::class.java)
		// intent.putExtra("user.iban", user.iban)
		// intent.putExtra("user.name", user.name)
		// intent.putExtra("access_point", userApi)
		startActivity(intent)
	}
}
