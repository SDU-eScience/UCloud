package dk.sdu.cloud

data class CommonErrorMessage(val why: String)
data class FindById<out IdType : Any>(val id: IdType)
data class FindByName(val name: String)
