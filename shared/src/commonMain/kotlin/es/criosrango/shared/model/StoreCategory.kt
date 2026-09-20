package es.criosrango.shared.model
import kotlinx.serialization.Serializable
@Serializable
data class StoreCategory(
    val id: Int = 0, val parent: Int = 0, val name: String = "", val slug: String = "",
    val count: Int = 0, val image: ProductImage? = null
)