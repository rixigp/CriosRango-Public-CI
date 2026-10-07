package es.criosrango.shared.promotions

class PromotionRepository(
    private val client: PromotionClient = PromotionClient()
) {
    suspend fun getPromotions(): List<Promotion> = client.getPromotions()

    fun close() = client.close()
}
