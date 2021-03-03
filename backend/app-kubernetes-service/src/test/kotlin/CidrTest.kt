package dk.sdu.cloud.service

import dk.sdu.cloud.app.kubernetes.services.IpUtils.formatIpAddress
import dk.sdu.cloud.app.kubernetes.services.IpUtils.isSafeToUse
import dk.sdu.cloud.app.kubernetes.services.IpUtils.remapAddress
import dk.sdu.cloud.app.kubernetes.services.IpUtils.validateCidr
import kotlin.test.*

class CidrTest {
    @Test
    fun `test basic case`() {
        val subnet = validateCidr("10.0.0.128/26")
        assertEquals("10.0.0.128", formatIpAddress(subnet.first))
        assertEquals("10.0.0.191", formatIpAddress(subnet.last))
    }

    @Test
    fun `testing remapping between subnets`() {
        val sourceSubnet = validateCidr("10.0.0.128/26")
        val destSubnet = validateCidr("20.0.0.128/26")
        val address = validateCidr("10.0.0.140/32").first

        assertEquals("10.0.0.140", formatIpAddress(address)) // sanity check
        assertEquals("20.0.0.140", formatIpAddress(remapAddress(address, sourceSubnet, destSubnet)))
    }

    @Test
    fun `test that IPs are detected as 'safe'`() {
        assertFalse(isSafeToUse(validateCidr("10.0.0.1/32").first))
        assertFalse(isSafeToUse(validateCidr("10.0.0.255/32").first))
        (2..254).forEach {
            assertTrue(isSafeToUse(validateCidr("10.0.0.$it/32").first))
        }
    }
}
