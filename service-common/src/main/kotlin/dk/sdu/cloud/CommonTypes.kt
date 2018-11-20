package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.client.RESTPathSegment
import dk.sdu.cloud.client.RESTQueryParameter
import dk.sdu.cloud.client.RequestPathSegmentMarshall
import dk.sdu.cloud.client.RequestQueryParamMarshall
import io.ktor.application.ApplicationCall

data class CommonErrorMessage(val why: String)

typealias FindByNameBulk = FindByName

data class FindByName(val name: String) {
    @get:JsonIgnore
    val bulk: List<String>
        get() = name.split(",")

    companion object {
        /**
         * Note this method is not universally accepted by all requests accepting a [FindByName]
         */
        fun bulk(names: List<String>): FindByName {
            if (names.any { it.contains(",") || it.contains("\n") }) {
                throw IllegalArgumentException("Name contains an invalid char: $names")
            }

            return FindByName(names.joinToString(","))
        }
    }
}

data class FindByStringId(val id: String) {
    @get:JsonIgnore
    val bulk: List<String>
        get() = id.split(",")

    companion object {
        /**
         * Note this method is not universally accepted by all requests accepting a [FindByStringId]
         */
        fun bulk(ids: List<String>): FindByName {
            if (ids.any { it.contains(",") || it.contains("\n") }) {
                throw IllegalArgumentException("ID contains an invalid char: $ids")
            }

            return FindByName(ids.joinToString(","))
        }
    }
}

data class FindByLongIdBulk(val id: List<Long>) {
    init {
        if (id.isEmpty()) throw IllegalArgumentException("id is empty")
    }

    constructor(vararg id: Long) : this (id.toList())

    companion object : RequestQueryParamMarshall<FindByLongIdBulk>, RequestPathSegmentMarshall<FindByLongIdBulk> {
        private fun deserialize(text: String): FindByLongIdBulk? {
            return text.split(",").mapNotNull { it.toLongOrNull() }.takeIf { it.isNotEmpty() }
                ?.let { FindByLongIdBulk(it) }
        }

        private fun serialize(bulk: FindByLongIdBulk): String {
            return bulk.id.joinToString(",")
        }

        override fun deserializeSegment(segment: RESTPathSegment<*>, call: ApplicationCall): Pair<String, Any?>? {
            if (segment !is RESTPathSegment.Property<*, *>) return null
            val name = segment.property.name

            val param = call.parameters[name] ?: return null
            return name to deserialize(param)
        }

        override fun serializePathSegment(segment: RESTPathSegment<*>, value: FindByLongIdBulk): String {
            return serialize(value)
        }

        override fun deserializeQueryParam(segment: RESTQueryParameter<*>, call: ApplicationCall): Pair<String, Any?>? {
            if (segment !is RESTQueryParameter.Property<*, *>) return null
            val name = segment.property.name

            val param = call.request.queryParameters[name] ?: return null
            return name to deserialize(param)
        }

        override fun serializeQueryParam(param: RESTQueryParameter<*>, value: FindByLongIdBulk): Pair<String, String> {
            if (param !is RESTQueryParameter.Property<*, *>) throw IllegalStateException()
            val name = param.property.name
            return name to serialize(value)
        }
    }
}

data class FindByLongId(val id: Long)

data class FindByIntIdBulk(val id: List<Int>) {
    init {
        if (id.isEmpty()) throw IllegalArgumentException("id is empty")
    }

    constructor(vararg id: Int) : this (id.toList())

    companion object : RequestQueryParamMarshall<FindByIntIdBulk>, RequestPathSegmentMarshall<FindByIntIdBulk> {
        private fun deserialize(text: String): FindByIntIdBulk? {
            return text.split(",").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
                ?.let { FindByIntIdBulk(it) }
        }

        private fun serialize(bulk: FindByIntIdBulk): String {
            return bulk.id.joinToString(",")
        }

        override fun deserializeSegment(segment: RESTPathSegment<*>, call: ApplicationCall): Pair<String, Any?>? {
            if (segment !is RESTPathSegment.Property<*, *>) return null
            val name = segment.property.name

            val param = call.parameters[name] ?: return null
            return name to deserialize(param)
        }

        override fun serializePathSegment(segment: RESTPathSegment<*>, value: FindByIntIdBulk): String {
            return serialize(value)
        }

        override fun deserializeQueryParam(segment: RESTQueryParameter<*>, call: ApplicationCall): Pair<String, Any?>? {
            if (segment !is RESTQueryParameter.Property<*, *>) return null
            val name = segment.property.name

            val param = call.request.queryParameters[name] ?: return null
            return name to deserialize(param)
        }

        override fun serializeQueryParam(param: RESTQueryParameter<*>, value: FindByIntIdBulk): Pair<String, String> {
            if (param !is RESTQueryParameter.Property<*, *>) throw IllegalStateException()
            val name = param.property.name
            return name to serialize(value)
        }
    }
}

data class FindByIntId(val id: Int)

data class FindByDoubleIdBulk(val id: List<Double>) {
    init {
        if (id.isEmpty()) throw IllegalArgumentException("id is empty")
    }

    constructor(vararg id: Double) : this (id.toList())

    companion object : RequestQueryParamMarshall<FindByDoubleIdBulk>, RequestPathSegmentMarshall<FindByDoubleIdBulk> {
        private fun deserialize(text: String): FindByDoubleIdBulk? {
            return text.split(",").mapNotNull { it.toDoubleOrNull() }.takeIf { it.isNotEmpty() }
                ?.let { FindByDoubleIdBulk(it) }
        }

        private fun serialize(bulk: FindByDoubleIdBulk): String {
            return bulk.id.joinToString(",")
        }

        override fun deserializeSegment(segment: RESTPathSegment<*>, call: ApplicationCall): Pair<String, Any?>? {
            if (segment !is RESTPathSegment.Property<*, *>) return null
            val name = segment.property.name

            val param = call.parameters[name] ?: return null
            return name to deserialize(param)
        }

        override fun serializePathSegment(segment: RESTPathSegment<*>, value: FindByDoubleIdBulk): String {
            return serialize(value)
        }

        override fun deserializeQueryParam(segment: RESTQueryParameter<*>, call: ApplicationCall): Pair<String, Any?>? {
            if (segment !is RESTQueryParameter.Property<*, *>) return null
            val name = segment.property.name

            val param = call.request.queryParameters[name] ?: return null
            return name to deserialize(param)
        }

        override fun serializeQueryParam(param: RESTQueryParameter<*>, value: FindByDoubleIdBulk): Pair<String, String> {
            if (param !is RESTQueryParameter.Property<*, *>) throw IllegalStateException()
            val name = param.property.name
            return name to serialize(value)
        }
    }
}

data class FindByDoubleId(val id: Double)

typealias BinaryStream = Unit
