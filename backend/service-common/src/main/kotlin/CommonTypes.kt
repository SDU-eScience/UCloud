package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiStable

@UCloudApiStable
@UCloudApiDoc("Generic error message")
data class CommonErrorMessage(
    @UCloudApiDoc("Human readable description of why the error occurred. This value is generally not stable.")
    val why: String,
    @UCloudApiDoc(
        "Machine readable description of why the error occurred. This value is stable and can be relied upon."
    )
    val errorCode: String? = null
)

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

data class FindByStringId(val id: String)

data class FindByLongId(val id: Long)
data class FindByIntId(val id: Int)
data class FindByDoubleId(val id: Double)
