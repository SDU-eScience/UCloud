package dk.sdu.cloud.indexing.utils

import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.indexing.api.AllOf
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.Comparison
import dk.sdu.cloud.indexing.api.ComparisonOperator
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.QueryRequest
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.indexing.services.ElasticIndexedFile

val fileQuery = FileQuery(
    listOf("/home/jonas@hinchely.dk"),
    null,
    AllOf(listOf(AnyOf(listOf("jonas@hinchely.dk"), negate = false))),
    null,
    AllOf(listOf(AnyOf(listOf(FileType.FILE, FileType.DIRECTORY), negate = false))),
    null,
    AllOf(
        listOf(
            AnyOf(listOf(Comparison(1542754800000, ComparisonOperator.LESS_THAN_EQUALS)), false), AnyOf(
                listOf(
                    Comparison(1539727200000, ComparisonOperator.GREATER_THAN_EQUALS)
                ), false
            )
        )
    )
)

val queryRequest = QueryRequest(fileQuery, itemsPerPage = 25, page = 0)
val minimumStatisticRequest = StatisticsRequest(fileQuery)

val elasticFile = ElasticIndexedFile(
    "/path/to/d",
    2,
    FileType.FILE,
    "12345678"
)

val eventMatStorFile = elasticFile


