package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore

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

data class FindByLongId(val id: Long)
data class FindByIntId(val id: Int)
data class FindByDoubleId(val id: Double)
