package es.criosrango.shared.promotions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromotionVisibilityTest {
    @Test
    fun anonymousCanSeePublicBlackFridayButNotPersonalPromotions() {
        val blackFriday = Promotion(id = "blackfriday", title = "Black Friday 20%", code = "blackcrios")
        val welcome = Promotion(id = "welcome", title = "Bienvenida 10%", code = "bienvenida")
        val birthday = Promotion(id = "birthday", title = "Cumpleaños 15%", code = "cr-cumple")
        val wallet = Promotion(id = "wallet", title = "Saldo de monedero", code = "cr-monedero-abc")
        val explicitlyPersonal = Promotion(id = "other", title = "Oferta", code = "OTHER", personal = true)
        val loginRequired = Promotion(id = "other2", title = "Oferta", code = "OTHER2", requiresLogin = true)

        assertTrue(blackFriday.isVisibleToAnonymous())
        assertFalse(welcome.isVisibleToAnonymous())
        assertFalse(birthday.isVisibleToAnonymous())
        assertFalse(wallet.isVisibleToAnonymous())
        assertFalse(explicitlyPersonal.isVisibleToAnonymous())
        assertFalse(loginRequired.isVisibleToAnonymous())
    }

    @Test
    fun authenticatedCartShowsEveryEligiblePromotionFromSharedSource() {
        val promotions = listOf(
            Promotion(id = "sale", title = "Promoción A", code = "SALE"),
            Promotion(id = "personal", title = "Promoción B", code = "WELCOME", personal = true, requiresLogin = true)
        )
        assertEquals(promotions, cartVisiblePromotions(promotions, authenticated = true))
    }

    @Test
    fun anonymousCartUsesTheSameGuestEligibilityFilter() {
        val promotions = listOf(
            Promotion(id = "sale", title = "Promoción A", code = "SALE"),
            Promotion(id = "personal", title = "Promoción B", code = "WELCOME", personal = true, requiresLogin = true)
        )
        assertEquals(listOf(promotions.first()), cartVisiblePromotions(promotions, authenticated = false))
    }
}
