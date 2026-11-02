package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountReturnActionTest {
    @Test fun singleProductFromOrdersUsesDirectWhatsAppAction() {
        assertEquals(AccountReturnAction.SINGLE_PRODUCT, accountReturnActionForItemCount(1))
    }

    @Test fun multipleProductsFromOrdersOpenSharedPicker() {
        assertEquals(AccountReturnAction.MULTI_PRODUCT, accountReturnActionForItemCount(2))
    }

    @Test fun emptyOrderHasNoReturnAction() {
        assertEquals(AccountReturnAction.NONE, accountReturnActionForItemCount(0))
    }
}
