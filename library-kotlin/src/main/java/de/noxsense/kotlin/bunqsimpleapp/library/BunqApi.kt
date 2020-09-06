package de.noxsense.kotlin.bunqsimpleapp.library

data class User(val apiToken: String, var name: String);

data class Payment(val id: String, val user: User, val timestamp: Long)  {
}
