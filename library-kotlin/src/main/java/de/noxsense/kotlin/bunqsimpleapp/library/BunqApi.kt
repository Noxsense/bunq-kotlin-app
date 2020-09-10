package de.noxsense.kotlin.bunqsimpleapp.library

data class User(val iban: String, var name: String) {

	/** List of payemts. If timestamp is zero, the payment is not send. */
	var payments: List<Payment> = listOf()
		private set

	/** Make and send a new payment.
	 * Add to cached payments. */
	fun newPayment(receiver: User, amountInCents: Int, reason: String) : Boolean
		= Payment(this, receiver, amountInCents, reason).also {
			payments += it
		}.send()

	override fun toString() : String
		= "$name ($iban)"

	override fun equals(other: Any?) : Boolean
		= other != null && other is User && other.iban == this.iban
}

data class Payment(
		val sender: User,
		val receiver: User,
		val amount: Int,
		val text: String
) {
	var timestamp : Long = 0
		private set

	/** Send the requested payment. */
	fun send() : Boolean
		= amount > 0 . also {
			timestamp = 0
		}

	override fun toString() : String
		= "Payment ($sender -> $receiver, $timestamp): ${amount/100} - $text"
}
