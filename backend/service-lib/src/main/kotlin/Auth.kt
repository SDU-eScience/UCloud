package dk.sdu.cloud

import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.calls.UCloudApiStable
import kotlinx.serialization.Serializable

/*
 * NOTE(Dan): __DO NOT__ add your own roles here. They go in the services, this is __only__ for a system-wide role.
 */
@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc(
    """
        Represents a `SecurityPrincipal`'s system-wide role.

        __This is usually not used for application-specific authorization.__

        Services are encouraged to implement their own authorization control, potentially
        from a common library.       
    """
)
enum class Role {
    @UCloudApiDoc("The security principal is an unauthenticated guest")
    GUEST,

    @UCloudApiDoc(
        """
            The security principal is a normal end-user.

            Normal end users can also have "admin-like" privileges in certain parts of the application.
        """
    )
    USER,

    @UCloudApiDoc(
        """
            The security principal is an administrator of the system.

            Very few users should have this role.
        """
    )
    ADMIN,

    @UCloudApiDoc("The security principal is a first party, __trusted__, service.")
    SERVICE,

    @UCloudApiDoc(
        """
            The security principal is some third party application.
            
            This type of role is currently not used. It is reserved for potential future purposes.
        """
    )
    THIRD_PARTY_APP,

    PROVIDER,

    @UCloudApiDoc(
        """
            The user role is unknown.

             If the action is somewhat low-sensitivity it should be fairly safe to assume `USER`/`THIRD_PARTY_APP`
             privileges. This means no special privileges should be granted to the user.
             
             This will only happen if we are sent a token of a newer version that what we cannot parse.
        """
    )
    UNKNOWN
}

object Roles {
    val AUTHENTICATED = setOf(Role.USER, Role.ADMIN, Role.SERVICE, Role.THIRD_PARTY_APP, Role.PROVIDER)
    val END_USER = setOf(Role.USER, Role.ADMIN)
    val PRIVILEGED = setOf(Role.ADMIN, Role.SERVICE)
    val SERVICE = setOf(Role.SERVICE)

    @Deprecated("Corrected spelling", replaceWith = ReplaceWith("Roles.PRIVILEGED"), DeprecationLevel.HIDDEN)
    val PRIVILEDGED = setOf(Role.ADMIN, Role.SERVICE)

    val ADMIN = setOf(Role.ADMIN)
    val THIRD_PARTY_APP = setOf(Role.THIRD_PARTY_APP)
    val PUBLIC = setOf(*Role.values())
    val PROVIDER = setOf(Role.PROVIDER, Role.SERVICE)
}

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc(
    """
        A minimal representation of a security principal.

        More information can be gathered from an auth service, using the username as a key.
    """
)
data class SecurityPrincipal(
    @UCloudApiDoc(
        """
            The unique username of this security principal.
            
            This is usually suitable for display in UIs.
        """
    )
    val username: String,

    @UCloudApiDoc("The role of the security principal")
    val role: Role,

    @UCloudApiDoc("The first name of the security principal. Can be empty.")
    val firstName: String,

    @UCloudApiDoc("The last name of the security principal. Can be empty.")
    val lastName: String,

    @UCloudApiDoc("The email of the user")
    val email: String? = null,

    @UCloudApiDoc("""
        A boolean flag indicating if the user has 2FA enabled for their user.
        
        If the token does not contain this information (old tokens generated before field's introduction) then this will
        be set to `true`. This is done to avoid breaking extended tokens. This behavior will should change in a
        future update.
       
        All new tokens _should_ contain this information explicitly.
    """)
    val twoFactorAuthentication: Boolean = true,

    val principalType: String? = null,

    @UCloudApiDoc("A boolean indicating if the service agreement has been accepted")
    val serviceAgreementAccepted: Boolean = false,

    val organization: String? = null
)

/**
 * Represents an access token issued for a security principal.
 *
 * SHOULD NOT CONTAIN SENSITIVE DATA (LIKE THE JWT)
 */
@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class SecurityPrincipalToken(
    val principal: SecurityPrincipal,

    /**
     * A list of scopes that this principal is currently authorized for.
     */
    val scopes: List<SecurityScope>,

    /**
     * When was this token issued (ms since unix epoch)
     */
    val issuedAt: Long,

    /**
     * When does this token expire (ms since unix epoch)
     */
    val expiresAt: Long,

    /**
     * An opaque token that uniquely identifies a refresh token.
     *
     * This session reference __must not__ be used by any client. This session reference will be embedded in JWTs.
     * This makes them readable by the end-user. It is __very__ important that we do not leak refresh tokens into
     * the JWT. This reference is added solely for the purpose of auditing.
     */
    val publicSessionReference: String?,

    /**
     * The username of the principal extending this token
     */
    val extendedBy: String? = null,

    /**
     * The chain of all token extensions.
     *
     * They are ordered from the first extension to the last extension. The extension chain will always include
     * [extendedBy] as the last element.
     *
     * An empty list implies that [extendedBy] is null.
     */
    val extendedByChain: List<String> = emptyList()

    // NOTE: DO NOT ADD SENSITIVE DATA TO THIS CLASS (INCLUDING JWT)
    // IT IS USED IN THE AUDIT SYSTEM
)

@UCloudApiOwnedBy(CoreTypes::class)
enum class AccessRight(val scopeName: String) {
    READ("read"),
    READ_WRITE("write")
}

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
data class SecurityScope internal constructor(
    val segments: List<String>,
    val access: AccessRight,
    val meta: Map<String, String> = emptyMap()
) {
    init {
        if (segments.isEmpty()) throw IllegalArgumentException("segments cannot be empty")
    }

    fun isCoveredBy(other: SecurityScope): Boolean {
        val accessLevelMatch = other.access == AccessRight.READ_WRITE || access == AccessRight.READ
        if (!accessLevelMatch) return false

        if (other.segments.first() == ALL_SCOPE && segments.first() != SPECIAL_SCOPE) {
            return true
        }

        if (segments.size < other.segments.size) return false

        for (i in other.segments.indices) {
            val otherSegment = other.segments[i]
            val thisSegment = segments[i]

            if (otherSegment != thisSegment) return false
        }

        return true
    }

    override fun toString(): String {
        return StringBuilder().apply {
            append(segments.joinToString("."))
            append(':')
            append(access.scopeName)
            if (meta.isNotEmpty()) {
                append(':')
                append(
                    meta.map { (key, rawValue) ->
                        val value = base64Encode(rawValue.encodeToByteArray())
                        "$key!$value"
                    }.joinToString(",")
                )
            }
        }.toString()
    }

    companion object {
        private val segmentRegex = Regex("[a-zA-Z0-9_\\-\\+]+")
        const val ALL_SCOPE = "all"
        const val SPECIAL_SCOPE = "special"

        val ALL_WRITE = SecurityScope.construct(listOf(ALL_SCOPE), AccessRight.READ_WRITE)
        val ALL_READ = SecurityScope.construct(listOf(ALL_SCOPE), AccessRight.READ)

        fun parseFromString(rawScope: String): SecurityScope {
            if (rawScope == "api") return SecurityScope(listOf("all"), AccessRight.READ_WRITE)

            val parts = rawScope.split(':')
            if (parts.size < 2) throw IllegalArgumentException("Not enough parts. '$rawScope'")
            val segments = parts.first().split('.')
            val firstInvalidSegment = segments.find { !it.matches(segmentRegex) }
            if (firstInvalidSegment != null) {
                throw IllegalArgumentException("Invalid segment found '$firstInvalidSegment' from '$rawScope'")
            }

            val normalizedAccess = parts[1].lowercase()
            val access = AccessRight.values().find { it.scopeName == normalizedAccess }
                ?: throw IllegalArgumentException("Bad access right in audience")

            val metadataString = parts.getOrNull(2)
            val metadata = if (metadataString == null) {
                emptyMap()
            } else {
                val kvPairs = metadataString.split(",").filter { it.isNotEmpty() }
                kvPairs.map { pair ->
                    val (key, b64Value) = pair.split('!').takeIf { it.size >= 2 }
                        ?: throw IllegalArgumentException("Missing kv pair in meta of '$rawScope'")
                    key to base64Decode(b64Value).decodeToString()
                }.toMap()
            }

            return SecurityScope(segments, access, metadata)
        }

        fun construct(
            segments: List<String>,
            access: AccessRight,
            meta: Map<String, String> = emptyMap()
        ): SecurityScope {
            val firstInvalidSegment = segments.find { !it.matches(segmentRegex) }
            if (firstInvalidSegment != null) {
                throw IllegalArgumentException("Invalid segment found '$firstInvalidSegment'")
            }

            return SecurityScope(segments, access)
        }
    }
}

private val encodingTable: CharArray = charArrayOf(
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W',
    'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
    'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/',
)

fun base64EncodeCommon(value: ByteArray): String {
    val size = value.size
    val mod = size % 3
    val padding = ((mod and 1) shl 1) + ((mod and 2) shr 1)
    val output = StringBuilder(4 * (size + padding) / 3)

    var ptr = 0
    while (ptr <= size - 3) {
        val b0 = (value[ptr].toInt() and 0xFF)
        val b1 = (value[ptr + 1].toInt() and 0xFF)
        val b2 = (value[ptr + 2].toInt() and 0xFF)
        output.append(encodingTable[b0 shr 2])
        output.append(encodingTable[(((0x3 and b0) shl 4) + (b1 shr 4))])
        output.append(encodingTable[(((0x0F and b1) shl 2) + (b2 shr 6))])
        output.append(encodingTable[(0x3F and b2)])
        ptr += 3
    }

    if (padding == 2) {
        val lastByte = (value[ptr].toInt() and 0xFF)
        output.append(encodingTable[(lastByte shr 2)])
        output.append(encodingTable[((0x3 and lastByte) shl 4)])
        output.append('=')
        output.append('=')
    } else if (padding == 1) {
        val b0 = value[ptr].toInt() and 0xFF
        val b1 = value[ptr + 1].toInt() and 0xFF
        output.append(encodingTable[(b0 shr 2)])
        output.append(encodingTable[(((0x3 and b0) shl 4) + (b1 shr 4))])
        output.append(encodingTable[(((0x0F and b1) shl 2))])
        output.append('=')
    }

    return output.toString()
}

private val decodingTable: IntArray = intArrayOf(
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 62, 0, 0, 0, 63, 52, 53,
    54, 55, 56, 57, 58, 59, 60, 61, 0, 0,
    0, 0, 0, 0, 0, 0, 1, 2, 3, 4,
    5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
    15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    25, 0, 0, 0, 0, 0, 0, 26, 27, 28,
    29, 30, 31, 32, 33, 34, 35, 36, 37, 38,
    39, 40, 41, 42, 43, 44, 45, 46, 47, 48,
    49, 50, 51, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0
)

@Suppress("DuplicatedCode", "UNUSED_CHANGED_VALUE")
fun base64DecodeCommon(value: String): ByteArray {
    val length = value.length
    if (length < 2) return ByteArray(0)
    val padding = run {
        if (value.endsWith("==")) 2
        else if (value.endsWith("=")) 1
        else 0
    }

    val output = ByteArray(3 * length / 4 - padding)
    var outputPtr = 0
    var ptr = 0
    while (ptr <= length - 4 - padding) {
        // NOTE(Dan): If the string is actually base64, then we shouldn't go out-of-bounds
        val in0 = decodingTable.getOrElse(value[ptr + 0].code) { 0 }
        val in1 = decodingTable.getOrElse(value[ptr + 1].code) { 0 }
        val in2 = decodingTable.getOrElse(value[ptr + 2].code) { 0 }
        val in3 = decodingTable.getOrElse(value[ptr + 3].code) { 0 }
        output[outputPtr++] = ((in0 shl 2) or (in1 shr 4)).toByte()
        output[outputPtr++] = ((in1 shl 4) or (in2 shr 2)).toByte()
        output[outputPtr++] = ((in2 shl 6) or in3).toByte()
        ptr += 4
    }

    if (padding == 2) {
        val in0 = decodingTable.getOrElse(value[ptr + 0].code) { 0 }
        val in1 = decodingTable.getOrElse(value[ptr + 1].code) { 0 }
        output[outputPtr++] = ((in0 shl 2) or (in1 shr 4)).toByte()
    } else if (padding == 1) {
        val in0 = decodingTable.getOrElse(value[ptr + 0].code) { 0 }
        val in1 = decodingTable.getOrElse(value[ptr + 1].code) { 0 }
        val in2 = decodingTable.getOrElse(value[ptr + 2].code) { 0 }
        output[outputPtr++] = ((in0 shl 2) or (in1 shr 4)).toByte()
        output[outputPtr++] = ((in1 shl 4) or (in2 shr 2)).toByte()
    }
    return output
}
