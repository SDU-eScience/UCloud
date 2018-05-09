[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [HPCApplicationDescriptions](./index.md)

# HPCApplicationDescriptions

`object HPCApplicationDescriptions : RESTDescriptions`

### Properties

| Name | Summary |
|---|---|
| [findByName](find-by-name.md) | `val findByName: RESTCallDescription<FindByName, `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationDescription`](../-application-description/index.md)`>, CommonErrorMessage>` |
| [findByNameAndVersion](find-by-name-and-version.md) | `val findByNameAndVersion: RESTCallDescription<`[`FindApplicationAndOptionalDependencies`](../-find-application-and-optional-dependencies/index.md)`, `[`ApplicationWithOptionalDependencies`](../-application-with-optional-dependencies/index.md)`, CommonErrorMessage>` |
| [listAll](list-all.md) | `val listAll: RESTCallDescription<`[`Unit`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/index.html)`, `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationSummary`](../-application-summary/index.md)`>, CommonErrorMessage>` |
