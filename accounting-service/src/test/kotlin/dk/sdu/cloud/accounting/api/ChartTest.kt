package dk.sdu.cloud.accounting.api

import org.junit.Test
import kotlin.test.assertEquals

class ChartTest{

    @Test
    fun `1D test`() {
        val chart1d = Chart<DataPoint1D<Long>>(listOf(DataPoint1D(100L,"Label"), DataPoint1D(200L)),"Hint")
        assertEquals(100L, chart1d.data.first().x)
        assertEquals("Label", chart1d.data.first().label)
        assertEquals("Hint", chart1d.chartTypeHint)
        //just for coverage
        chart1d.hashCode()
        chart1d.toString()
    }

    @Test
    fun `2D test`() {
        val chart2d = Chart<DataPoint2D<Long, Long>>(listOf(DataPoint2D(100L,100L,"Label"), DataPoint2D(200L, 200L)),"Hint")
        assertEquals(100L, chart2d.data.first().x)
        assertEquals(100L, chart2d.data.first().y)
        assertEquals("Label", chart2d.data.first().label)
        assertEquals("Hint", chart2d.chartTypeHint)
        //just for coverage
        chart2d.hashCode()
        chart2d.toString()
    }

    @Test
    fun `3D test`() {
        val chart3d = Chart<DataPoint3D<Long, Long, Long>>(listOf(DataPoint3D(100L,100L,100L,"Label"), DataPoint3D(200L,200L,200L)),"Hint")
        assertEquals(100L, chart3d.data.first().x)
        assertEquals(100L, chart3d.data.first().y)
        assertEquals(100L, chart3d.data.first().z)
        assertEquals("Label", chart3d.data.first().label)
        assertEquals("Hint", chart3d.chartTypeHint)
        //just for coverage
        chart3d.hashCode()
        chart3d.toString()
    }

}
