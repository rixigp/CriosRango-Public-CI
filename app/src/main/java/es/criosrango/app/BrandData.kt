package es.criosrango.app

data class BrandTerm(
    val id: Int = 0,
    val name: String,
    val slug: String,
    val count: Int = 0,
    val imageUrl: String? = null,
    val localLogoRes: Int? = null
)

/**
 * Legacy packaged logos are retained only as an offline/missing-image fallback.
 * The live brand catalogue is loaded from WooCommerce product_brand.
 */
val APP_BRANDS = listOf(
    BrandTerm(name = "Mayoral", slug = "mayoral", localLogoRes = R.drawable.brand_mayoral),
    BrandTerm(name = "Boboli", slug = "boboli", localLogoRes = R.drawable.brand_boboli),
    BrandTerm(name = "Tiffosi", slug = "tiffosi", localLogoRes = R.drawable.brand_tiffosi),
    BrandTerm(name = "Spagnolo", slug = "spagnolo", localLogoRes = R.drawable.brand_spagnolo),
    BrandTerm(name = "Surkana", slug = "surkana", localLogoRes = R.drawable.brand_surkana),
    BrandTerm(name = "Ragussa", slug = "ragussa", localLogoRes = R.drawable.brand_ragussa),
    BrandTerm(name = "Abel & Lula", slug = "abel-lula", localLogoRes = R.drawable.brand_abel_lula),
    BrandTerm(name = "Yoedu", slug = "yoedu", localLogoRes = R.drawable.brand_yoedu),
    BrandTerm(name = "Babidu", slug = "babidu", localLogoRes = R.drawable.brand_babidu),
    BrandTerm(name = "Amaya", slug = "amaya", localLogoRes = R.drawable.brand_amaya),
    BrandTerm(name = "Betzzia", slug = "betzzia", localLogoRes = R.drawable.brand_betzzia),
    BrandTerm(name = "Carla Ruiz", slug = "carla-ruiz", localLogoRes = R.drawable.brand_carla_ruiz),
    BrandTerm(name = "Arggido", slug = "arggido", localLogoRes = R.drawable.brand_arggido),
    BrandTerm(name = "Marta en Brazil", slug = "marta-en-brazil", localLogoRes = R.drawable.brand_marta_en_brazil),
    BrandTerm(name = "Micolino", slug = "micolino", localLogoRes = R.drawable.brand_micolino),
    BrandTerm(name = "Carmy", slug = "carmy", localLogoRes = R.drawable.brand_carmy),
    BrandTerm(name = "Varones", slug = "varones", localLogoRes = R.drawable.brand_varones),
    BrandTerm(name = "Moncho Heredia", slug = "moncho-heredia", localLogoRes = R.drawable.brand_moncho_heredia),
    BrandTerm(name = "Dadati", slug = "dadati", localLogoRes = R.drawable.brand_dadati),
    BrandTerm(name = "Selinac", slug = "selinac", localLogoRes = R.drawable.brand_selinac)
)

internal fun BrandTerm.withPackagedLogoFallback(): BrandTerm {
    if (localLogoRes != null) return this
    val fallback = APP_BRANDS.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
    return if (fallback != null) copy(localLogoRes = fallback.localLogoRes) else this
}
