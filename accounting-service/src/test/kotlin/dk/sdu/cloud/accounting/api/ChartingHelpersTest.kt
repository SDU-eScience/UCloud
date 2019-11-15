package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.service.test.TestUsers
import org.junit.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertTrue

class ChartingHelpersTest {

    //mainly just CC tests

    @Test (expected = IllegalArgumentException::class)
    fun `sum chart from events test - no desired data points`() {
        ChartingHelpers.sumChartFromEvents(emptyList(), 0, dataSelector = {StorageUsedEvent(1234,123,123456,TestUsers.user.username).bytesUsed})
    }

    @Test
    fun `sum chart from events test - no events`() {
        val chart = ChartingHelpers.sumChartFromEvents(emptyList(), dataSelector = {StorageUsedEvent(1234,123,123456,TestUsers.user.username).bytesUsed})
        assertTrue(chart.data.isEmpty())
    }

    @Test
    fun `sum chart from events test` () {
        val chart = ChartingHelpers.sumChartFromEvents(
            listOf(
                StorageUsedEvent(1234,12312451,123,TestUsers.user.username),
                StorageUsedEvent(1241,12315412,321,TestUsers.user.username)
            ),
            dataSelector = {StorageUsedEvent(1234,12312451,123,TestUsers.user.username).bytesUsed}
        )
    }

    @Test (expected = IllegalArgumentException::class)
    fun `absolute chart from events test - no desired data points`() {
        ChartingHelpers.absoluteChartFromEvents(emptyList(), 0, dataSelector = {StorageUsedEvent(1234,123,123456,TestUsers.user.username).bytesUsed})
    }

    @Test
    fun `absolute chart from events test - no events`() {
        val chart = ChartingHelpers.absoluteChartFromEvents(emptyList(), dataSelector = {StorageUsedEvent(1234,123,123456,TestUsers.user.username).bytesUsed})
        assertTrue(chart.data.isEmpty())
    }

    @Test
    fun `absolute chart from events test` () {
        val chart = ChartingHelpers.absoluteChartFromEvents(
            listOf(
                StorageUsedEvent(1234,12312451,123,TestUsers.user.username),
                StorageUsedEvent(1241,12315412,321,TestUsers.user.username)
            ),
            dataSelector = {StorageUsedEvent(1234,12312451,123,TestUsers.user.username).bytesUsed}
        )
    }

}
