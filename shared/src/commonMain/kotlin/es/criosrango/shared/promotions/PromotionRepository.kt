package es.criosrango.shared.promotions

class PromotionRepository(
    private val client: PromotionClient? = null,
    bearerTokenProvider: (() -> String?)? = null
) {
    private val resolvedClient = client ?: PromotionClient(bearerTokenProvider = bearerTokenProvider)

    suspend fun getPromotions(): List<Promotion> = resolvedClient.getPromotions()

    fun close() = resolvedClient.close()
}
