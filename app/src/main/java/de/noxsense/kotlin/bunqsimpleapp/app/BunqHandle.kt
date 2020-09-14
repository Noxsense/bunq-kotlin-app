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

object BunqHandle {

	private val TAG = "Nox-Bunq-MainActivity"

	val confFile: String = "bunq-sandbox.conf"

	fun postNewSandboxUser() : SandboxUser {
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
			return SandboxUser.fromJsonReader(
				JsonReader(StringReader(apiKey.toString())))
		}
		throw BunqException("Could not post a new SandboxUser.")
	}
}
