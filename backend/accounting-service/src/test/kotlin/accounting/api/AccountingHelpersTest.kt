package dk.sdu.cloud.accounting.api

import org.junit.Test
import kotlin.test.assertEquals

class AccountingHelpersTest {

    @Test
    fun `test of accounting helpers`() {

        val date = 631195200000 //1990/01/01 13:00 GMT+1

        val start = AccountingHelpers.startOfPeriod(date)

        val end = AccountingHelpers.endOfPeriod(date)

        assertEquals(631148400000, start)
        assertEquals(633826799999, end)

        //Just for 100 cc
        AccountingHelpers.startOfPeriod()
        AccountingHelpers.endOfPeriod()
    }

}
