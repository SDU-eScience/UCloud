package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import java.io.InputStream
import java.net.URLEncoder
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

var trustManagerForHttpClient: TrustManager? = null

fun createHttpClient() = HttpClient(CIO) {
    expectSuccess = false

    engine {
        requestTimeout = 1000 * 60 * 5

        val customTrustManager = trustManagerForHttpClient
        if (customTrustManager != null) {
            https {
                trustManager = customTrustManager
            }
        }
    }
}

fun createWebsocketClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets)
    expectSuccess = false
    engine {
        requestTimeout = 0

        val customTrustManager = trustManagerForHttpClient
        if (customTrustManager != null) {
            https {
                trustManager = customTrustManager
            }
        }
    }
}

fun urlEncode(value: String): String {
    return URLEncoder.encode(value, "UTF-8")
}

fun createCustomX509TrustManagerFromStore(store: InputStream, password: CharArray): TrustManager {
    val initialTrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    initialTrustManager.init(null as KeyStore?)

    val defaultX509Manager = initialTrustManager.trustManagers.asSequence()
        .filterIsInstance<X509TrustManager>().first()

    val additionalTrustManager = run {
        // Reload with new keys
        val myTrustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        myTrustStore.load(store, password)

        store.close()

        val instance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        instance.init(myTrustStore)

        instance
    }

    val additionalX509Manager = additionalTrustManager.trustManagers.asSequence()
        .filterIsInstance<X509TrustManager>().first()

    return object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = defaultX509Manager.acceptedIssuers
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                additionalX509Manager.checkServerTrusted(chain, authType);
            } catch (e: CertificateException) {
                defaultX509Manager.checkServerTrusted(chain, authType);
            }
        }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            defaultX509Manager.checkClientTrusted(chain, authType);
        }
    }
}

fun loadAndInitializeMissingCaCertsForLauncherInDevelopmentModeOnlyPlease() {
    trustManagerForHttpClient = createCustomX509TrustManagerFromStore(
        Unit::class.java.classLoader.getResourceAsStream("missing_ca_certs.jks")
            ?: error("Could not locate additional keys"),

        // NOTE(Dan): Store contains only public information. We are not leaking anything by giving you this super
        // secret password.
        "devpassword".toCharArray()
    )
}
