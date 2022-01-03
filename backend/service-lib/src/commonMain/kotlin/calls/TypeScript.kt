package dk.sdu.cloud.calls

@Target(AnnotationTarget.CLASS)
annotation class TSDefinition(val code: String)

@Target(AnnotationTarget.CLASS)
annotation class TSTopLevel

@Target(AnnotationTarget.CLASS)
annotation class TSNamespace(val namespace: String)

@Target(AnnotationTarget.CLASS)
annotation class TSSkipCodegen()

const val OAS_TS_DEF = "x-ucloud-ts"
