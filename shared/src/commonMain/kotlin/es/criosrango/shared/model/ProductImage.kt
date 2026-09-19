package es.criosrango.shared.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
data class ProductImage(
    val id: Int = 0, val src: String = "", val thumbnail: String = "", val alt: String = "",
    val name: String = "", @SerialName("srcset") val srcSet: String = ""
)