package de.noxsense.kotlin.bunqsimpleapp.library

/** BunqAPI.
  * according to their official documentations.
  * @url https://github.com/bunq/doc/blob/develop/README.md
  */

// data class User;
// - userPerson with /user-person
// - userCompany with /user-company
// - userPaymentServiceProvider with /user-payment-service-provider
// - userId on GET /user
// can create bank accounts, order cards, make payments

// data class MonetaryAccount;
// - holder of money, linked to legal bank account owner
// - most API counts with payments need monetary-accountId
// - GET /user/userId/monetary-account
// - three types:
// 1. MonetaryAccountBank /user/userId/monetary-account-bank (classical single bank)
// 2. MonetaryAccountSavings /user/userId/monetary-account-savings (auto-saving account)
// 3. MonetaryAccountJoint /user/userId/monetary-account-joint (shared with other users)

// data class Payment;
// - in and outcoming

// data class RequestInquiry;
// data class Card;
// data class Attachment;
// data class NoteAttachment;

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
