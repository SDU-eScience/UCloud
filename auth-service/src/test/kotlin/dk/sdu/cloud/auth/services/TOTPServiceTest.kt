package dk.sdu.cloud.auth.services

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.util.*

class TOTPServiceTest {
    @Test
    fun `test creating a secret`() {
        WSTOTPService().createSharedSecret()
    }

    @Test
    fun `test verifying a secret`() {
        val totpService = WSTOTPService()
        val (secretBase32Encoded, _, _, _, _) = totpService.createSharedSecret()
        assertFalse(totpService.verify(secretBase32Encoded, 123456))
    }

    @Test
    fun `test conversion to otpauth URI`() {
        val service = WSTOTPService()
        val displayName = "displayName"
        val issuer = "sducloud"
        val createSharedSecret = service.createSharedSecret()
        val uri = createSharedSecret.toOTPAuthURI(displayName, issuer)

        assertEquals("otpauth", uri.scheme)
        assertEquals("totp", uri.host)
        assertEquals("/$issuer:$displayName", uri.path)
        val params = uri.queryParams()
        assertEquals(createSharedSecret.secretBase32Encoded, params["secret"])
        assertEquals(createSharedSecret.algorithm.uriName, params["algorithm"])
        assertEquals(createSharedSecret.numberOfDigits, params["digits"]!!.toInt())
        assertEquals(createSharedSecret.periodInSeconds, params["period"]!!.toInt())
        assertEquals(issuer, params["issuer"])
    }

    private fun URI.queryParams(): Map<String, String> {
        val queryPairs = LinkedHashMap<String, String>()
        val query = query
        val pairs = query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        return queryPairs
    }

}