package dk.sdu.cloud.share.services.db

import com.github.jasync.sql.db.RowData

fun <T, ST : SqlType<T>> RowData.getField(field: SQLField<ST>): T {
    return getAs(field.name)
}

fun <T, ST : SqlType<T>> RowData.getFieldNullable(field: SQLField<ST>): T? {
    return getAs(field.name)
}
