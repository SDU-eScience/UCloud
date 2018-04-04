package dk.sdu.cloud.storage.services.ext.irods

import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class ChainedTrustManager(
    private val headTrustManager: X509TrustManager,
    private val defaultManager: X509TrustManager
) : X509TrustManager {
    companion object {
        private val defaultTrustManager by lazy {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?) // Initialization of default trust store

            tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        fun chainDefaultWithStoreFrom(input: InputStream, password: CharArray): ChainedTrustManager {
            val defaultStore = defaultTrustManager

            val store = input.use {
                val store = KeyStore.getInstance(KeyStore.getDefaultType())
                store.load(input, password)
                store
            }

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(store)

            val loadedStore = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()

            return ChainedTrustManager(loadedStore, defaultStore)
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            headTrustManager.checkClientTrusted(chain, authType)
        } catch (ex: CertificateException) {
            defaultManager.checkClientTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            headTrustManager.checkServerTrusted(chain, authType)
        } catch (ex: CertificateException) {
            defaultManager.checkServerTrusted(chain, authType)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        val default = defaultManager.acceptedIssuers
        val additional = headTrustManager.acceptedIssuers
        return additional + default
    }

    fun setAsDefault() {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(this), null)

        SSLContext.setDefault(context)
    }
}