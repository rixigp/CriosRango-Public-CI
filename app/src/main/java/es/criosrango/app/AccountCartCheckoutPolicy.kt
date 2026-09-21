package es.criosrango.app

import es.criosrango.shared.account.AccountCustomerAddress

/**
 * Pure I.3 account/cart/checkout rules.
 *
 * The Woo cart/session and PendingCardPaymentStore remain independent state stores.
 * This object only expresses UI/integration decisions so they can be regression-tested.
 */
internal object AccountCartCheckoutPolicy {
    fun showGuestLoginCta(accountUserId: Int?, cartItemCount: Int): Boolean =
        accountUserId == null && cartItemCount > 0

    fun shouldPrefillAccountAddress(
        existingCheckoutAddress: CustomerAddress?,
        accountAddress: AccountCustomerAddress?
    ): Boolean =
        existingCheckoutAddress == null && accountAddress != null

    fun isAddressVisibleToAccount(ownerAccountId: Int?, currentAccountId: Int?): Boolean =
        ownerAccountId == null || ownerAccountId == currentAccountId

    fun hasClaimCredentials(orderId: Int?, orderKey: String?): Boolean =
        orderId != null && orderId > 0 && !orderKey.isNullOrBlank()
}
