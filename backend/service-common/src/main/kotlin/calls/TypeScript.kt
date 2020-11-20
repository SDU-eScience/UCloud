package dk.sdu.cloud.calls

@Target(AnnotationTarget.CLASS)
annotation class TSDefinition(val code: String)

@Target(AnnotationTarget.CLASS)
annotation class TSTopLevel

const val OAS_TS_DEF = "x-ucloud-ts"
