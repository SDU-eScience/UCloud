package dk.sdu.cloud.accounting.api

/**
 * A base data point.
 *
 * Doesn't contain any actual data, but contains metadata about a data point.
 *
 * @see ChartDataPoint1D
 * @see ChartDataPoint2D
 * @see ChartDataPoint3D
 */
interface ChartDataPoint {
    val label: String?
}

interface ChartDataPoint1D<XType> : ChartDataPoint {
    val x: XType
}

interface ChartDataPoint2D<XType, YType> : ChartDataPoint1D<XType> {
    val y: YType
}

interface ChartDataPoint3D<XType, YType, ZType> : ChartDataPoint2D<XType, YType> {
    val z: ZType
}

/**
 * A simple [ChartDataPoint] of one dimension
 *
 * @see ChartDataPoint
 */
data class DataPoint1D<XType>(
    override val x: XType,
    override val label: String? = null
) : ChartDataPoint1D<XType>

/**
 * A simple [ChartDataPoint] of two dimensions
 *
 * @see ChartDataPoint
 */
data class DataPoint2D<XType, YType>(
    override val x: XType,
    override val y: YType,
    override val label: String? = null
) : ChartDataPoint2D<XType, YType>

/**
 * A simple [ChartDataPoint] of three dimensions
 *
 * @see ChartDataPoint
 */
data class DataPoint3D<XType, YType, ZType>(
    override val x: XType,
    override val y: YType,
    override val z: ZType,
    override val label: String? = null
) : ChartDataPoint3D<XType, YType, ZType>

data class Chart<DataPointType : ChartDataPoint>(
    val data: List<DataPointType>,

    /**
     * A hint for the chart rendering system.
     *
     * This can be any value and is only used as a hint for the API consumer. API consumer should not rely on this
     * value. It may be used as a hint to people who explore the data.
     */
    val chartTypeHint: String? = null,

    /**
     * An array of data types. Each element corresponds to a dimension.
     *
     * The data types are typically values from [ChartDataTypes], but are allowed
     * to be of a different type.
     */
    val dataTypes: List<String?>? = null,

    val dataTitle: String? = null
)

object ChartDataTypes {
    val BYTES = "bytes"
    val DURATION = "duration"
    val DATE = "date"
    val DATETIME = "datetime"
    val NUMBER = "number"
}
