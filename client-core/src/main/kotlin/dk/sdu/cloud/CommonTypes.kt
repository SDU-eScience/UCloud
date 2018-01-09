package dk.sdu.cloud

data class CommonErrorMessage(val why: String)
data class FindByName(val name: String)

data class FindByStringId(val id: String)
data class FindByLongId(val id: Long)
data class FindByIntId(val id: Int)
data class FindByDoubleId(val id: Double)
