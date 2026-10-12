package es.criosrango.shared

import es.criosrango.shared.generated.resources.Res
import org.jetbrains.compose.resources.DrawableResource

/** Current Android subcategory mapping expressed by stable WooCommerce slugs (Outlet excluded). */
internal data class StoreSubcategoryAsset(val parentSlug: String, val subcategorySlug: String, val resource: DrawableResource)

internal val STORE_SUBCATEGORY_ASSETS: List<StoreSubcategoryAsset> = listOf(
    StoreSubcategoryAsset("bebe-nina", "abrigos-y-cazadoras", Res.drawable.subcat_bebe_nina_abrigos_cazadoras),
    StoreSubcategoryAsset("bebe-nina", "ropa-de-bano", Res.drawable.subcat_bebe_nina_ropa_bano),
    StoreSubcategoryAsset("bebe-nina", "ropa-de-sport", Res.drawable.subcat_bebe_nina_ropa_sport),
    StoreSubcategoryAsset("bebe-nina", "ropa-de-vestir", Res.drawable.subcat_bebe_nina_ropa_vestir),
    StoreSubcategoryAsset("bebe-nino", "abrigos-y-cazadoras", Res.drawable.subcat_bebe_nino_abrigos_cazadoras),
    StoreSubcategoryAsset("bebe-nino", "ropa-de-bano", Res.drawable.subcat_bebe_nino_ropa_bano),
    StoreSubcategoryAsset("bebe-nino", "ropa-de-sport", Res.drawable.subcat_bebe_nino_ropa_sport),
    StoreSubcategoryAsset("bebe-nino", "ropa-de-vestir", Res.drawable.subcat_bebe_nino_ropa_vestir),
    StoreSubcategoryAsset("nina", "abrigos-y-cazadoras", Res.drawable.subcat_nina_abrigos_cazadoras),
    StoreSubcategoryAsset("nina", "calzado", Res.drawable.subcat_nina_calzado),
    StoreSubcategoryAsset("nina", "ropa-de-bano", Res.drawable.subcat_nina_ropa_bano),
    StoreSubcategoryAsset("nina", "ropa-de-sport", Res.drawable.subcat_nina_ropa_sport),
    StoreSubcategoryAsset("nina", "ropa-de-vestir", Res.drawable.subcat_nina_ropa_vestir),
    StoreSubcategoryAsset("nino", "abrigos-y-cazadoras", Res.drawable.subcat_nino_abrigos_cazadoras),
    StoreSubcategoryAsset("nino", "ropa-de-bano", Res.drawable.subcat_nino_ropa_bano),
    StoreSubcategoryAsset("nino", "ropa-de-sport", Res.drawable.subcat_nino_ropa_sport),
    StoreSubcategoryAsset("nino", "ropa-de-vestir", Res.drawable.subcat_nino_ropa_vestir),
    StoreSubcategoryAsset("recien-nacido", "abrigos-y-cazadoras", Res.drawable.subcat_recien_nacido_abrigos_cazadoras),
    StoreSubcategoryAsset("recien-nacido", "bodys", Res.drawable.subcat_recien_nacido_bodys),
    StoreSubcategoryAsset("recien-nacido", "complementos", Res.drawable.subcat_recien_nacido_complementos),
    StoreSubcategoryAsset("recien-nacido", "jesusitos-y-vestidos", Res.drawable.subcat_recien_nacido_jesusitos_vestidos),
    StoreSubcategoryAsset("recien-nacido", "peleles-y-conjuntos", Res.drawable.subcat_recien_nacido_peleles_conjuntos),
    StoreSubcategoryAsset("hombre", "abrigos-y-cazadoras", Res.drawable.subcat_hombre_abrigos_cazadoras),
    StoreSubcategoryAsset("hombre", "americanas-y-trajes", Res.drawable.subcat_hombre_americanas_trajes),
    StoreSubcategoryAsset("hombre", "camisas", Res.drawable.subcat_hombre_camisas),
    StoreSubcategoryAsset("hombre", "camisetas-y-polos", Res.drawable.subcat_hombre_camisetas_polos),
    StoreSubcategoryAsset("hombre", "complementos-y-bano", Res.drawable.subcat_hombre_complementos_bano),
    StoreSubcategoryAsset("hombre", "jerseis-y-chaquetas", Res.drawable.subcat_hombre_jerseis_chaquetas),
    StoreSubcategoryAsset("hombre", "pantalones-y-bermudas", Res.drawable.subcat_hombre_pantalones_bermudas),
    StoreSubcategoryAsset("hombre", "sudaderas", Res.drawable.subcat_hombre_sudaderas),
    StoreSubcategoryAsset("mujer", "abrigos-y-cazadoras", Res.drawable.subcat_mujer_abrigos_cazadoras),
    StoreSubcategoryAsset("mujer", "camisas-y-camisetas", Res.drawable.subcat_mujer_camisas_camisetas),
    StoreSubcategoryAsset("mujer", "chaquetas-y-chalecos", Res.drawable.subcat_mujer_chaquetas_chalecos),
    StoreSubcategoryAsset("mujer", "complementos", Res.drawable.subcat_mujer_complementos),
    StoreSubcategoryAsset("mujer", "jerseis", Res.drawable.subcat_mujer_jerseis),
    StoreSubcategoryAsset("mujer", "pantalones-y-faldas", Res.drawable.subcat_mujer_pantalones_faldas),
    StoreSubcategoryAsset("mujer", "ropa-de-fiesta", Res.drawable.subcat_mujer_ropa_fiesta),
    StoreSubcategoryAsset("mujer", "vestidos-conjuntos-monos", Res.drawable.subcat_mujer_vestidos_conjuntos_monos),
    StoreSubcategoryAsset("ropa-de-fiesta", "conjuntos-y-monos", Res.drawable.subcat_mujer_fiesta_conjuntos_monos),
    StoreSubcategoryAsset("ropa-de-fiesta", "vestidos-de-fiesta", Res.drawable.subcat_mujer_fiesta_vestidos)
)

internal fun storeSubcategoryResource(parentSlug: String, subcategorySlug: String): DrawableResource? {
    val stableParent = parentSlug.trim().lowercase()
    val rawChild = subcategorySlug.trim().lowercase()
    val stableChild = when (rawChild) {
        "jerseys-y-chaquetas" -> "jerseis-y-chaquetas"
        "jerseys" -> "jerseis"
        "vestidos-conjuntos-y-monos", "vestidos-conjuntos-monos-casual", "vestidos-conjuntos-y-monos-casual" -> "vestidos-conjuntos-monos"
        else -> rawChild
    }
    return STORE_SUBCATEGORY_ASSETS.firstOrNull { it.parentSlug == stableParent && it.subcategorySlug == stableChild }?.resource
}
