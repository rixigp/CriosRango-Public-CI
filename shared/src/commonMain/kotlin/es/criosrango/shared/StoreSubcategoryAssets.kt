package es.criosrango.shared

import es.criosrango.shared.generated.resources.Res
import es.criosrango.shared.generated.resources.outlet_hombre_invierno
import es.criosrango.shared.generated.resources.outlet_hombre_verano
import es.criosrango.shared.generated.resources.outlet_mujer_invierno
import es.criosrango.shared.generated.resources.outlet_mujer_verano
import es.criosrango.shared.generated.resources.outlet_nina_invierno
import es.criosrango.shared.generated.resources.outlet_nina_verano
import es.criosrango.shared.generated.resources.outlet_nino_invierno
import es.criosrango.shared.generated.resources.outlet_nino_verano
import es.criosrango.shared.generated.resources.subcat_bebe_nina_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_bebe_nina_ropa_bano
import es.criosrango.shared.generated.resources.subcat_bebe_nina_ropa_sport
import es.criosrango.shared.generated.resources.subcat_bebe_nina_ropa_vestir
import es.criosrango.shared.generated.resources.subcat_bebe_nino_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_bebe_nino_ropa_bano
import es.criosrango.shared.generated.resources.subcat_bebe_nino_ropa_sport
import es.criosrango.shared.generated.resources.subcat_bebe_nino_ropa_vestir
import es.criosrango.shared.generated.resources.subcat_nina_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_nina_calzado
import es.criosrango.shared.generated.resources.subcat_nina_ropa_bano
import es.criosrango.shared.generated.resources.subcat_nina_ropa_sport
import es.criosrango.shared.generated.resources.subcat_nina_ropa_vestir
import es.criosrango.shared.generated.resources.subcat_nino_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_nino_ropa_bano
import es.criosrango.shared.generated.resources.subcat_nino_ropa_sport
import es.criosrango.shared.generated.resources.subcat_nino_ropa_vestir
import es.criosrango.shared.generated.resources.subcat_recien_nacido_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_recien_nacido_bodys
import es.criosrango.shared.generated.resources.subcat_recien_nacido_complementos
import es.criosrango.shared.generated.resources.subcat_recien_nacido_jesusitos_vestidos
import es.criosrango.shared.generated.resources.subcat_recien_nacido_peleles_conjuntos
import es.criosrango.shared.generated.resources.subcat_hombre_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_hombre_americanas_trajes
import es.criosrango.shared.generated.resources.subcat_hombre_camisas
import es.criosrango.shared.generated.resources.subcat_hombre_camisetas_polos
import es.criosrango.shared.generated.resources.subcat_hombre_complementos_bano
import es.criosrango.shared.generated.resources.subcat_hombre_jerseis_chaquetas
import es.criosrango.shared.generated.resources.subcat_hombre_pantalones_bermudas
import es.criosrango.shared.generated.resources.subcat_hombre_sudaderas
import es.criosrango.shared.generated.resources.subcat_mujer_abrigos_cazadoras
import es.criosrango.shared.generated.resources.subcat_mujer_camisas_camisetas
import es.criosrango.shared.generated.resources.subcat_mujer_chaquetas_chalecos
import es.criosrango.shared.generated.resources.subcat_mujer_complementos
import es.criosrango.shared.generated.resources.subcat_mujer_jerseis
import es.criosrango.shared.generated.resources.subcat_mujer_pantalones_faldas
import es.criosrango.shared.generated.resources.subcat_mujer_ropa_fiesta
import es.criosrango.shared.generated.resources.subcat_mujer_vestidos_conjuntos_monos
import es.criosrango.shared.generated.resources.subcat_mujer_fiesta_conjuntos_monos
import es.criosrango.shared.generated.resources.subcat_mujer_fiesta_vestidos
import es.criosrango.shared.model.StoreCategory
import org.jetbrains.compose.resources.DrawableResource

/**
 * Shared equivalent of Android subcategoryImageRes(): keys use the same
 * normalized parent/child category names, not assumed WooCommerce slugs.
 */
internal data class StoreSubcategoryAsset(
    val parentName: String,
    val subcategoryName: String,
    val resource: DrawableResource
)

internal val STORE_SUBCATEGORY_ASSETS: List<StoreSubcategoryAsset> = listOf(
    StoreSubcategoryAsset("bebe nina", "abrigos y cazadoras", Res.drawable.subcat_bebe_nina_abrigos_cazadoras),
    StoreSubcategoryAsset("bebe nina", "ropa de bano", Res.drawable.subcat_bebe_nina_ropa_bano),
    StoreSubcategoryAsset("bebe nina", "ropa de sport", Res.drawable.subcat_bebe_nina_ropa_sport),
    StoreSubcategoryAsset("bebe nina", "ropa de vestir", Res.drawable.subcat_bebe_nina_ropa_vestir),
    StoreSubcategoryAsset("bebe nino", "abrigos y cazadoras", Res.drawable.subcat_bebe_nino_abrigos_cazadoras),
    StoreSubcategoryAsset("bebe nino", "ropa de bano", Res.drawable.subcat_bebe_nino_ropa_bano),
    StoreSubcategoryAsset("bebe nino", "ropa de sport", Res.drawable.subcat_bebe_nino_ropa_sport),
    StoreSubcategoryAsset("bebe nino", "ropa de vestir", Res.drawable.subcat_bebe_nino_ropa_vestir),
    StoreSubcategoryAsset("nina", "abrigos y cazadoras", Res.drawable.subcat_nina_abrigos_cazadoras),
    StoreSubcategoryAsset("nina", "calzado", Res.drawable.subcat_nina_calzado),
    StoreSubcategoryAsset("nina", "ropa de bano", Res.drawable.subcat_nina_ropa_bano),
    StoreSubcategoryAsset("nina", "ropa de sport", Res.drawable.subcat_nina_ropa_sport),
    StoreSubcategoryAsset("nina", "ropa de vestir", Res.drawable.subcat_nina_ropa_vestir),
    StoreSubcategoryAsset("nino", "abrigos y cazadoras", Res.drawable.subcat_nino_abrigos_cazadoras),
    StoreSubcategoryAsset("nino", "ropa de bano", Res.drawable.subcat_nino_ropa_bano),
    StoreSubcategoryAsset("nino", "ropa de sport", Res.drawable.subcat_nino_ropa_sport),
    StoreSubcategoryAsset("nino", "ropa de vestir", Res.drawable.subcat_nino_ropa_vestir),
    StoreSubcategoryAsset("recien nacido", "abrigos y cazadoras", Res.drawable.subcat_recien_nacido_abrigos_cazadoras),
    StoreSubcategoryAsset("recien nacido", "bodys", Res.drawable.subcat_recien_nacido_bodys),
    StoreSubcategoryAsset("recien nacido", "complementos", Res.drawable.subcat_recien_nacido_complementos),
    StoreSubcategoryAsset("recien nacido", "jesusitos y vestidos", Res.drawable.subcat_recien_nacido_jesusitos_vestidos),
    StoreSubcategoryAsset("recien nacido", "peleles y conjuntos", Res.drawable.subcat_recien_nacido_peleles_conjuntos),
    StoreSubcategoryAsset("hombre", "abrigos y cazadoras", Res.drawable.subcat_hombre_abrigos_cazadoras),
    StoreSubcategoryAsset("hombre", "americanas y trajes", Res.drawable.subcat_hombre_americanas_trajes),
    StoreSubcategoryAsset("hombre", "camisas", Res.drawable.subcat_hombre_camisas),
    StoreSubcategoryAsset("hombre", "camisetas y polos", Res.drawable.subcat_hombre_camisetas_polos),
    StoreSubcategoryAsset("hombre", "complementos y bano", Res.drawable.subcat_hombre_complementos_bano),
    StoreSubcategoryAsset("hombre", "jerseis y chaquetas", Res.drawable.subcat_hombre_jerseis_chaquetas),
    StoreSubcategoryAsset("hombre", "pantalones y bermudas", Res.drawable.subcat_hombre_pantalones_bermudas),
    StoreSubcategoryAsset("hombre", "sudaderas", Res.drawable.subcat_hombre_sudaderas),
    StoreSubcategoryAsset("mujer", "abrigos y cazadoras", Res.drawable.subcat_mujer_abrigos_cazadoras),
    StoreSubcategoryAsset("mujer", "camisas y camisetas", Res.drawable.subcat_mujer_camisas_camisetas),
    StoreSubcategoryAsset("mujer", "chaquetas y chalecos", Res.drawable.subcat_mujer_chaquetas_chalecos),
    StoreSubcategoryAsset("mujer", "complementos", Res.drawable.subcat_mujer_complementos),
    StoreSubcategoryAsset("mujer", "jerseis", Res.drawable.subcat_mujer_jerseis),
    StoreSubcategoryAsset("mujer", "pantalones y faldas", Res.drawable.subcat_mujer_pantalones_faldas),
    StoreSubcategoryAsset("mujer", "ropa de fiesta", Res.drawable.subcat_mujer_ropa_fiesta),
    StoreSubcategoryAsset("mujer", "vestidos conjuntos monos", Res.drawable.subcat_mujer_vestidos_conjuntos_monos),
    StoreSubcategoryAsset("ropa de fiesta", "conjuntos y monos", Res.drawable.subcat_mujer_fiesta_conjuntos_monos),
    StoreSubcategoryAsset("ropa de fiesta", "vestidos de fiesta", Res.drawable.subcat_mujer_fiesta_vestidos),
    StoreSubcategoryAsset("outlet hombre", "invierno", Res.drawable.outlet_hombre_invierno),
    StoreSubcategoryAsset("outlet hombre", "verano", Res.drawable.outlet_hombre_verano),
    StoreSubcategoryAsset("outlet mujer", "invierno", Res.drawable.outlet_mujer_invierno),
    StoreSubcategoryAsset("outlet mujer", "verano", Res.drawable.outlet_mujer_verano),
    StoreSubcategoryAsset("outlet nina", "invierno", Res.drawable.outlet_nina_invierno),
    StoreSubcategoryAsset("outlet nina", "verano", Res.drawable.outlet_nina_verano),
    StoreSubcategoryAsset("outlet nino", "invierno", Res.drawable.outlet_nino_invierno),
    StoreSubcategoryAsset("outlet nino", "verano", Res.drawable.outlet_nino_verano),
    StoreSubcategoryAsset("outlet", "hombre invierno", Res.drawable.outlet_hombre_invierno),
    StoreSubcategoryAsset("outlet", "hombre verano", Res.drawable.outlet_hombre_verano),
    StoreSubcategoryAsset("outlet", "mujer invierno", Res.drawable.outlet_mujer_invierno),
    StoreSubcategoryAsset("outlet", "mujer verano", Res.drawable.outlet_mujer_verano),
    StoreSubcategoryAsset("outlet", "nina invierno", Res.drawable.outlet_nina_invierno),
    StoreSubcategoryAsset("outlet", "nina verano", Res.drawable.outlet_nina_verano),
    StoreSubcategoryAsset("outlet", "nino invierno", Res.drawable.outlet_nino_invierno),
    StoreSubcategoryAsset("outlet", "nino verano", Res.drawable.outlet_nino_verano),
    StoreSubcategoryAsset("outlet", "outlet hombre invierno", Res.drawable.outlet_hombre_invierno),
    StoreSubcategoryAsset("outlet", "outlet hombre verano", Res.drawable.outlet_hombre_verano),
    StoreSubcategoryAsset("outlet", "outlet mujer invierno", Res.drawable.outlet_mujer_invierno),
    StoreSubcategoryAsset("outlet", "outlet mujer verano", Res.drawable.outlet_mujer_verano),
    StoreSubcategoryAsset("outlet", "outlet nina invierno", Res.drawable.outlet_nina_invierno),
    StoreSubcategoryAsset("outlet", "outlet nina verano", Res.drawable.outlet_nina_verano),
    StoreSubcategoryAsset("outlet", "outlet nino invierno", Res.drawable.outlet_nino_invierno),
    StoreSubcategoryAsset("outlet", "outlet nino verano", Res.drawable.outlet_nino_verano),
)

private fun normalizeAndroidCategoryName(value: String): String =
    value.lowercase().trim()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
        .replace('ñ', 'n')

internal fun storeSubcategoryResource(parent: StoreCategory, category: StoreCategory): DrawableResource? {
    val parentName = normalizeAndroidCategoryName(parent.name)
    val rawChild = normalizeAndroidCategoryName(category.name)
    val childName = when {
        parentName == "hombre" && rawChild == "jerseys y chaquetas" -> "jerseis y chaquetas"
        parentName == "mujer" && rawChild == "jerseys" -> "jerseis"
        parentName == "mujer" && rawChild.contains("vestidos") && rawChild.contains("conjuntos") ->
            "vestidos conjuntos monos"
        else -> rawChild
    }
    return STORE_SUBCATEGORY_ASSETS.firstOrNull {
        it.parentName == parentName && it.subcategoryName == childName
    }?.resource
}
