package es.criosrango.shared.account

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Keeps the response contract compatible with the previous Gson models:
 * String fields may arrive as JSON strings or JSON numbers.
 * Explicit nulls are handled by the shared Json configuration's
 * coerceInputValues=true and the property's default value.
 */
object OrderStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("es.criosrango.shared.account.OrderString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder == null) return decoder.decodeString()
        return jsonDecoder.decodeJsonElement().jsonPrimitive.contentOrNull.orEmpty()
    }
}
