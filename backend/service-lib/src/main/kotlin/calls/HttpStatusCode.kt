package dk.sdu.cloud.calls

// Based on https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
data class HttpStatusCode(val value: Int, val description: String) {
    fun isSuccess(): Boolean = value in 200..299

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as HttpStatusCode

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value
    }

    companion object {
        val values = Array(600) { HttpStatusCode(it, "S$it") }

        fun parse(value: Int): HttpStatusCode = values.getOrElse(value) { HttpStatusCode(it, "S$it") }

        val Continue = HttpStatusCode(100, "Continue").also { values[100] = it }
        val SwitchingProtocols = HttpStatusCode(101, "Switching Protocols").also { values[101] = it }
        val Processing = HttpStatusCode(102, "Processing").also { values[102] = it }
        val EarlyHints = HttpStatusCode(103, "Early Hints").also { values[103] = it }

        val OK = HttpStatusCode(200, "OK").also { values[200] = it }
        val Created = HttpStatusCode(201, "Created").also { values[201] = it }
        val Accepted = HttpStatusCode(202, "Accepted").also { values[202] = it }
        val NonAuthoritativeInformation = HttpStatusCode(203, "Non-Authoritative Information").also { values[203] = it }
        val NoContent = HttpStatusCode(204, "No Content").also { values[204] = it }
        val ResetContent = HttpStatusCode(205, "Reset Content").also { values[205] = it }
        val PartialContent = HttpStatusCode(206, "Partial Content").also { values[206] = it }
        val MultiStatus = HttpStatusCode(207, "Multi-Status").also { values[207] = it }
        val AlreadyReported = HttpStatusCode(208, "Already Reported").also { values[208] = it }
        val IMUsed = HttpStatusCode(226, "IM Used").also { values[226] = it }

        val MultipleChoices = HttpStatusCode(300, "Multiple Choices").also { values[300] = it }
        val MovedPermanently = HttpStatusCode(301, "Moved Permanently").also { values[301] = it }
        val Found = HttpStatusCode(302, "Found").also { values[302] = it }
        val SeeOther = HttpStatusCode(303, "See Other").also { values[303] = it }
        val NotModified = HttpStatusCode(304, "Not Modified").also { values[304] = it }
        val UseProxy = HttpStatusCode(305, "Use Proxy").also { values[305] = it }
        val SwitchProxy = HttpStatusCode(306, "Switch Proxy").also { values[306] = it }
        val TemporaryRedirect = HttpStatusCode(307, "Temporary Redirect").also { values[307] = it }
        val PermanentRedirect = HttpStatusCode(308, "Permanent Redirect").also { values[308] = it }

        val BadRequest = HttpStatusCode(400, "Bad Request").also { values[400] = it }
        val Unauthorized = HttpStatusCode(401, "Unauthorized").also { values[401] = it }
        val PaymentRequired = HttpStatusCode(402, "Payment Required").also { values[402] = it }
        val Forbidden = HttpStatusCode(403, "Forbidden").also { values[403] = it }
        val NotFound = HttpStatusCode(404, "Not Found").also { values[404] = it }
        val MethodNotAllowed = HttpStatusCode(405, "Method Not Allowed").also { values[405] = it }
        val NotAcceptable = HttpStatusCode(406, "Not Acceptable").also { values[406] = it }
        val ProxyAuthenticationRequired = HttpStatusCode(407, "Proxy Authentication Required").also { values[407] = it }
        val RequestTimeout = HttpStatusCode(408, "Request Timeout").also { values[408] = it }
        val Conflict = HttpStatusCode(409, "Conflict").also { values[409] = it }
        val Gone = HttpStatusCode(410, "Gone").also { values[410] = it }
        val LengthRequired = HttpStatusCode(411, "Length Required").also { values[411] = it }
        val PreconditionFailed = HttpStatusCode(412, "Precondition Failed").also { values[412] = it }
        val PayloadTooLarge = HttpStatusCode(413, "Payload Too Large").also { values[413] = it }
        val URITooLong = HttpStatusCode(414, "URI Too Long").also { values[414] = it }
        val UnsupportedMediaType = HttpStatusCode(415, "Unsupported Media Type").also { values[415] = it }
        val RangeNotSatisfiable = HttpStatusCode(416, "Range Not Satisfiable").also { values[416] = it }
        val ExpectationFailed = HttpStatusCode(417, "Expectation Failed").also { values[417] = it }
        val ImATeapot = HttpStatusCode(418, "I'm a teapot").also { values[418] = it }
        val MisdirectedRequest = HttpStatusCode(421, "Misdirected Request").also { values[421] = it }
        val UnprocessableEntity = HttpStatusCode(422, "Unprocessable Entity").also { values[422] = it }
        val Locked = HttpStatusCode(423, "Locked").also { values[423] = it }
        val FailedDependency = HttpStatusCode(424, "Failed Dependency").also { values[424] = it }
        val TooEarly = HttpStatusCode(425, "Too Early").also { values[425] = it }
        val UpgradeRequired = HttpStatusCode(426, "Upgrade Required").also { values[426] = it }
        val PreconditionRequired = HttpStatusCode(428, "Precondition Required").also { values[428] = it }
        val TooManyRequests = HttpStatusCode(429, "Too Many Requests").also { values[429] = it }
        val RequestHeaderFieldsTooLarge = HttpStatusCode(431, "Request Header Fields Too Large").also { values[431] = it }
        val UnavailableForLegalReasons = HttpStatusCode(451, "Unavailable For Legal Reasons").also { values[451] = it }

        val InternalServerError = HttpStatusCode(500, "Internal Server Error").also { values[500] = it }
        val NotImplemented = HttpStatusCode(501, "Not Implemented").also { values[501] = it }
        val BadGateway = HttpStatusCode(502, "Bad Gateway").also { values[502] = it }
        val ServiceUnavailable = HttpStatusCode(503, "Service Unavailable").also { values[503] = it }
        val GatewayTimeout = HttpStatusCode(504, "Gateway Timeout").also { values[504] = it }
        val HTTPVersionNotSupported = HttpStatusCode(505, "HTTP Version Not Supported").also { values[505] = it }
        val VariantAlsoNegotiates = HttpStatusCode(506, "Variant Also Negotiates").also { values[506] = it }
        val InsufficientStorage = HttpStatusCode(507, "Insufficient Storage").also { values[507] = it }
        val LoopDetected = HttpStatusCode(508, "Loop Detected").also { values[508] = it }
        val NotExtended = HttpStatusCode(510, "Not Extended").also { values[510] = it }
        val NetworkAuthenticationRequired = HttpStatusCode(511, "Network Authentication Required").also { values[511] = it }
    }
}
