package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.CallDescription
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
class QueryParameterEncoder(
    call: CallDescription<*, *, *>,
    override val serializersModule: SerializersModule = EmptySerializersModule,
) : AbstractEncoder() {
    val builder: MutableMap<String, List<String>> = HashMap()
    private var encodingName: String? = null

    private fun append(value: String) {
        val encodingName = encodingName ?: return
        builder[encodingName] = (builder[encodingName] ?: emptyList()) + value
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return super.beginStructure(descriptor)
    }

    override fun encodeBoolean(value: Boolean) {
        append(value.toString())
    }

    override fun encodeByte(value: Byte) {
        append(value.toString())
    }

    override fun encodeChar(value: Char) {
        append(value.toString())
    }

    override fun encodeDouble(value: Double) {
        append(value.toString())
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        encodingName = descriptor.getElementName(index)
        return true
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        append(enumDescriptor.getElementName(index))
    }

    override fun encodeFloat(value: Float) {
        append(value.toString())
    }

    override fun encodeInt(value: Int) {
        append(value.toString())
    }

    override fun encodeLong(value: Long) {
        append(value.toString())
    }

    override fun encodeNull() {
        // Append nothing
    }

    override fun encodeShort(value: Short) {
        append(value.toString())
    }

    override fun encodeString(value: String) {
        append(value.toString())
    }

    override fun encodeValue(value: Any) {
        super.encodeValue(value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        super.endStructure(descriptor)
    }
}
