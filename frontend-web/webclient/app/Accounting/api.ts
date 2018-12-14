export interface DataPoint {
    label: string | null
}

export interface DataPoint1D<X = number> extends DataPoint {
    x: X
}

export interface DataPoint2D<X = number, Y = number> extends DataPoint1D<X> {
    y: Y
}

export interface DataPoint3D<X = number, Y = number, Z = number> extends DataPoint2D<X, Y> {
    z: Z
}

export interface Chart<Point extends DataPoint> {
    chartTypeHint?: string
    data: Point[]

    dataTitle?: string

    /**
     * An array of data types. Each element corresponds to a dimension. 
     * 
     * The data types are typically values from ChartDataTypes, but are allowed
     * to be of a different type.
     */
    dataTypes?: (string | null)[]
}

/**
 * Contains known data types. 
 * 
 * A data type is allowed to not be one of the following.
 */
export namespace ChartDataTypes {
    export const BYTES = "bytes"
    export const DURATION = "duration"
    export const DATE = "date"
    export const DATETIME = "datetime"
    export const NUMBER = "number";
}