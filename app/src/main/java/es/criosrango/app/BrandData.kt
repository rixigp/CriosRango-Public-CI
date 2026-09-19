package es.criosrango.app

data class BrandTerm(
    val name: String,
    val slug: String,
    val localLogoRes: Int
)

val APP_BRANDS = listOf(
    BrandTerm("Mayoral", "mayoral", R.drawable.brand_mayoral),
    BrandTerm("Boboli", "boboli", R.drawable.brand_boboli),
    BrandTerm("Tiffosi", "tiffosi", R.drawable.brand_tiffosi),
    BrandTerm("Spagnolo", "spagnolo", R.drawable.brand_spagnolo),
    BrandTerm("Surkana", "surkana", R.drawable.brand_surkana),
    BrandTerm("Ragussa", "ragussa", R.drawable.brand_ragussa),
    BrandTerm("Abel & Lula", "abel-lula", R.drawable.brand_abel_lula),
    BrandTerm("Yoedu", "yoedu", R.drawable.brand_yoedu),
    BrandTerm("Babidu", "babidu", R.drawable.brand_babidu),
    BrandTerm("Amaya", "amaya", R.drawable.brand_amaya),
    BrandTerm("Betzzia", "betzzia", R.drawable.brand_betzzia),
    BrandTerm("Carla Ruiz", "carla-ruiz", R.drawable.brand_carla_ruiz),
    BrandTerm("Arggido", "arggido", R.drawable.brand_arggido),
    BrandTerm("Marta en Brazil", "marta-en-brazil", R.drawable.brand_marta_en_brazil),
    BrandTerm("Micolino", "micolino", R.drawable.brand_micolino),
    BrandTerm("Carmy", "carmy", R.drawable.brand_carmy),
    BrandTerm("Varones", "varones", R.drawable.brand_varones),
    BrandTerm("Moncho Heredia", "moncho-heredia", R.drawable.brand_moncho_heredia),
    BrandTerm("Dadati", "dadati", R.drawable.brand_dadati),
    BrandTerm("Selinac", "selinac", R.drawable.brand_selinac)
)
