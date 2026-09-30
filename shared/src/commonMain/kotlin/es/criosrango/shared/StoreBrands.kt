package es.criosrango.shared

data class StoreBrand(val name: String, val slug: String)

val storeBrands = listOf(
    StoreBrand("Mayoral","mayoral"), StoreBrand("Boboli","boboli"), StoreBrand("Tiffosi","tiffosi"),
    StoreBrand("Spagnolo","spagnolo"), StoreBrand("Surkana","surkana"), StoreBrand("Ragussa","ragussa"),
    StoreBrand("Abel & Lula","abel-lula"), StoreBrand("Yoedu","yoedu"), StoreBrand("Babidu","babidu"),
    StoreBrand("Amaya","amaya"), StoreBrand("Betzzia","betzzia"), StoreBrand("Carla Ruiz","carla-ruiz"),
    StoreBrand("Arggido","arggido"), StoreBrand("Marta en Brazil","marta-en-brazil"), StoreBrand("Micolino","micolino"),
    StoreBrand("Carmy","carmy"), StoreBrand("Varones","varones"), StoreBrand("Moncho Heredia","moncho-heredia"),
    StoreBrand("Dadati","dadati"), StoreBrand("Selinac","selinac")
)