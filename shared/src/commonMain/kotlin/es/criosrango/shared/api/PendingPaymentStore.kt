package es.criosrango.shared.api

import es.criosrango.shared.model.PendingPayment

interface PendingPaymentStore {
    fun load(): PendingPayment?
    fun save(payment: PendingPayment)
    fun clear()

}
