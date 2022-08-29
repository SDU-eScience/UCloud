package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.RowData

/**
 * Provides a type set wrapper around [RowData.getAs]
 *
 * This function does not use the [SQLField.notNull] property. Use the nullable version of this function if the
 * field is nullable.
 */
fun <T, ST : SqlType<T>> RowData.getField(field: SQLField<ST>): T {
    return getAs(field.name)
}

/**
 * Provides a type set wrapper around [RowData.getAs]
 *
 * This function does not use the [SQLField.notNull] property. Use the non-nullable version of this function if the
 * field is not-null.
 */
fun <T, ST : SqlType<T>> RowData.getFieldNullable(field: SQLField<ST>): T? {
    return getAs(field.name)
}
