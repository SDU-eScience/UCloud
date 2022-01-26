package dk.sdu.cloud.calls

data class HttpMethod(val value: String) {
    companion object {
        val Get = HttpMethod("GET")
        val Post = HttpMethod("POST")
        val Head = HttpMethod("HEAD")
        val Put = HttpMethod("PUT")
        val Patch = HttpMethod("PATCH")
        val Delete = HttpMethod("Delete")
        val Options = HttpMethod("OPTIONS")
    }
}
