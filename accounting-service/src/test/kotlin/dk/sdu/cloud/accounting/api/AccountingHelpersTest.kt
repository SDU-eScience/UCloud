package dk.sdu.cloud.accounting.api

import org.junit.Test
import kotlin.test.assertEquals

class AccoutningHelpersTest{
    
    @Ignore
    @Test
    fun `test start and end of period`() {
        val acc = AccountingHelpers
        val start = acc.startOfPeriod()
        val end = acc.endOfPeriod()
        // approx a month (29,999999 days)
        assertEquals(2591999999, end-start)
    }

}
