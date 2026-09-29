package es.criosrango.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OutletAvailability(
    @SerialName("schema_version") val schemaVersion: Int = 0,
    val version: Long = 0L,
    val outlets: List<OutletAvailabilityBucket> = emptyList()
)

@Serializable
data class OutletAvailabilityBucket(
    @SerialName("outlet_category_id") val outletCategoryId: Int,
    val counts: List<OutletAvailabilityCount> = emptyList()
)

@Serializable
data class OutletAvailabilityCount(
    @SerialName("category_id") val categoryId: Int,
    val count: Int
)

fun OutletAvailability.countFor(outletCategoryId: Int, categoryId: Int): Int =
    outlets.firstOrNull { it.outletCategoryId == outletCategoryId }
        ?.counts
        ?.firstOrNull { it.categoryId == categoryId }
        ?.count
        ?: 0
