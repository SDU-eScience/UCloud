package dk.sdu.cloud.tcp

import dk.sdu.cloud.http.ByteBuffer
import dk.sdu.cloud.utils.fileExists
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import mbed.*

// Example: https://github.com/ARMmbed/mbedtls/blob/development/programs/ssl/ssl_client1.c

private fun UInt.mbedtlsErrorUnwrap(message: String) {
    if (this != 0U) {
        throw MbedTlsException(message, this.toInt())
    }
}

private fun Int.mbedtlsErrorUnwrap(message: String) {
    if (this != 0) {
        throw MbedTlsException(message, this)
    }
}

private fun Int.mbedtlsNegativeErrorUnwrap(message: String) {
    if (this != 0) {
        throw MbedTlsException(message, this)
    }
}

class MbedTlsException(message: String, errorCode: Int) : RuntimeException("mbedtls error: $message ($errorCode)")

private val locatedCaCertificate = atomic<String?>(null)
private const val CENTOS_LOCATION = "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem"
private const val UBUNTU_LOCATION = "/etc/ssl/certs/ca-certificates.crt"

private fun locateCaCertificate(): String {
    val cached = locatedCaCertificate.value
    if (cached != null) return cached
    val location = when {
        fileExists(CENTOS_LOCATION) -> CENTOS_LOCATION
        fileExists(UBUNTU_LOCATION) -> UBUNTU_LOCATION
        else -> throw MbedTlsException("Could not locate root certificate", -1)
    }

    locatedCaCertificate.compareAndSet(null, location)
    return location
}

class TlsTcpConnection(
    private val hostname: String,
    private val port: Int,
) : TcpConnection {
    private val arena = Arena()
    private var isOpen = false

    private val serverFd: mbedtls_net_context = arena.alloc()
    private val ssl: mbedtls_ssl_context = arena.alloc()
    private val conf: mbedtls_ssl_config = arena.alloc()
    private val cacert: mbedtls_x509_crt = arena.alloc()
    private val ctrdrbg: mbedtls_ctr_drbg_context = arena.alloc()
    private val entropy: mbedtls_entropy_context = arena.alloc()

    init {
        try {
            // Initialize structures required for the connection. These are freed in the 'finally' block.
            mbedtls_net_init(serverFd.ptr)
            mbedtls_ssl_init(ssl.ptr)
            mbedtls_ssl_config_init(conf.ptr)
            mbedtls_x509_crt_init(cacert.ptr)
            mbedtls_ctr_drbg_init(ctrdrbg.ptr)
            mbedtls_entropy_init(entropy.ptr)

            // Seed the random number generator
            mbedtls_ctr_drbg_seed(
                ctrdrbg.ptr,
                staticCFunction { a, b, c -> mbedtls_entropy_func(a, b, c) },
                entropy.ptr,
                null,
                0
            ).mbedtlsErrorUnwrap("mbedtls_ctr_drbg_seed")

            // Parse the provided CA certificate file
            mbedtls_x509_crt_parse_file(cacert.ptr, locateCaCertificate())
                .mbedtlsNegativeErrorUnwrap("mbedtls_x509_crt_parse_file")

            // Start connection
            mbedtls_net_connect(serverFd.ptr, hostname, port.toString(), MBEDTLS_NET_PROTO_TCP)
                .mbedtlsErrorUnwrap("mbedtls_net_connect")

            // Configure with default parameters. According to the documentation these are supposed to be "reasonable".
            // Given that this is a security focused library, I am assuming that this means that they will be somewhat
            // up-to-date.
            mbedtls_ssl_config_defaults(
                conf.ptr, MBEDTLS_SSL_IS_CLIENT, MBEDTLS_SSL_TRANSPORT_STREAM,
                MBEDTLS_SSL_PRESET_DEFAULT
            ).mbedtlsErrorUnwrap("mbedtls_ssl_config_defaults")

            // Configure with more reasonable defaults. We require that everything looks OK.
            mbedtls_ssl_conf_authmode(conf.ptr, MBEDTLS_SSL_VERIFY_REQUIRED)
            mbedtls_ssl_conf_ca_chain(conf.ptr, cacert.ptr, null)
            mbedtls_ssl_conf_rng(
                conf.ptr,
                staticCFunction { a, b, c -> mbedtls_ctr_drbg_random(a, b, c) },
                ctrdrbg.ptr
            )

            mbedtls_ssl_setup(ssl.ptr, conf.ptr).mbedtlsErrorUnwrap("mbedtls_ssl_setup")
            mbedtls_ssl_set_hostname(ssl.ptr, hostname).mbedtlsErrorUnwrap("mbedtls_ssl_set_hostname")
            mbedtls_ssl_set_bio(
                ssl.ptr,
                serverFd.ptr,
                staticCFunction { a, b, c -> mbedtls_net_send(a, b, c) },
                staticCFunction { a, b, c -> mbedtls_net_recv(a, b, c) },
                null
            )

            // Perform handshake and verify the result
            while (true) {
                val ret = mbedtls_ssl_handshake(ssl.ptr)
                if (ret == 0) break
                if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE) {
                    ret.mbedtlsErrorUnwrap("mbedtls_ssl_handshake")
                }
            }

            mbedtls_ssl_get_verify_result(ssl.ptr).mbedtlsErrorUnwrap("mbedtls_ssl_get_verify_result")
            isOpen = true

            // Connection is now ready to be used
            println("we have connected!")
        } catch (ex: Throwable) {
            close()
            throw ex
        }
    }

    override fun isOpen(): Boolean = isOpen

    override fun read(buffer: ByteBuffer) {
        if (!isOpen) throw MbedTlsException("Connection is closed (isOpen() = false)", -1)
        if (buffer.writerSpaceRemaining() == 0) throw MbedTlsException("No space left in buffer", -1)
        val ret = mbedtls_ssl_read(
            ssl.ptr,
            buffer.rawMemoryPinned.addressOf(buffer.writerIndex).reinterpret(),
            buffer.writerSpaceRemaining().toULong()
        )

        if (ret >= 0) {
            buffer.writerIndex += ret
            return
        }

        when (ret) {
            MBEDTLS_ERR_SSL_WANT_READ, MBEDTLS_ERR_SSL_WANT_WRITE -> {
                return
            }
            MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY -> {
                close()
            }
            else -> {
                close()
                throw MbedTlsException("mbedtls_ssl_read", ret)
            }
        }
    }

    override fun write(buffer: ByteBuffer) {
        if (!isOpen) throw MbedTlsException("Connection is closed (isOpen() = false)", -1)

        while (buffer.readerRemaining() > 0) {
            val ret = mbedtls_ssl_write(
                ssl.ptr,
                buffer.rawMemoryPinned.addressOf(buffer.readerIndex).reinterpret(),
                buffer.readerRemaining().toULong()
            )

            buffer.readerIndex += ret
            if (ret >= 0) continue

            if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE) {
                close()
                ret.mbedtlsNegativeErrorUnwrap("mbedtls_ssl_write")
            }
        }
    }

    override fun close() {
        mbedtls_net_free(serverFd.ptr) // This also closes the connection
        mbedtls_x509_crt_free(cacert.ptr)
        mbedtls_ssl_config_free(conf.ptr)
        mbedtls_ctr_drbg_free(ctrdrbg.ptr)
        mbedtls_entropy_free(entropy.ptr)
        mbedtls_ssl_close_notify(ssl.ptr)
        isOpen = false
    }
}
