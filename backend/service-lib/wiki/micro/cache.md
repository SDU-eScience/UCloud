The `service-lib` library provides a simple key-value cache. The cache is thread-safe and uses a non-blocking 
`Mutex`. The data stored in the cache is local to the micro-service instance. The primary use-case for this cache
is to save data from other micro-services locally, which is useful for a small amount of time.
The `SimpleCache` implementation integrates with `integration-testing` and they are automatically cleared at the
end of any test.

__Example:__ Using the `SimpleCache`

```kotlin
val apps = SimpleCache<NameAndVersion, Application>(SimpleCache.DONT_EXPIRE) { nv ->
    AppStore.findByNameAndVersion
        .call(FindApplicationAndOptionalDependencies(nv.name, nv.version), serviceClient)
        .throwIfInternal()
        .orNull()
        ?.let { Application(it.metadata, it.invocation) }
}

appService.apps.get(NameAndVersion("appName", "appVersion"))
```
