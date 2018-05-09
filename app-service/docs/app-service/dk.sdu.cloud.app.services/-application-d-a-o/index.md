[app-service](../../index.md) / [dk.sdu.cloud.app.services](../index.md) / [ApplicationDAO](./index.md)

# ApplicationDAO

`object ApplicationDAO`

### Properties

| Name | Summary |
|---|---|
| [inMemoryDB](in-memory-d-b.md) | `val inMemoryDB: `[`MutableMap`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/index.html)`<`[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationDescription`](../../dk.sdu.cloud.app.api/-application-description/index.md)`>>` |

### Functions

| Name | Summary |
|---|---|
| [all](all.md) | `fun all(): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationDescription`](../../dk.sdu.cloud.app.api/-application-description/index.md)`>` |
| [findAllByName](find-all-by-name.md) | `fun findAllByName(name: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`List`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/index.html)`<`[`ApplicationDescription`](../../dk.sdu.cloud.app.api/-application-description/index.md)`>` |
| [findByNameAndVersion](find-by-name-and-version.md) | `fun findByNameAndVersion(name: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`, version: `[`String`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/index.html)`): `[`ApplicationDescription`](../../dk.sdu.cloud.app.api/-application-description/index.md)`?` |
