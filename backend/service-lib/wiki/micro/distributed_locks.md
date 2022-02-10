`service-lib` supports distributed locks using [Redis](https://redis.io). They provide an interface and 
functionality similar to a `Mutex` but guarantees that only one instance in UCloud can hold the lock at the same time.
This is useful in a production environment where multiple instance of every micro-service runs.

__Example:__ Creating and using a distributed lock

```kotlin
val distributedLocks = DistributedLockBestEffortFactory(micro)
val lock = distributedLocks.create("metadata-recovery-lock", duration = 60_000)
while (true) {
    val didAcquire = lock.acquire()
    if (didAcquire) {
        processing@while (true) {
            // Do work here
            if (!lock.renew(60_000)) {
                log.info("We lost the lock!")
                break@processing
            }
        }
    }
    // Introduce randomness to make it more likely that clients don't try simultaneously
    delay(15000 + Random.nextLong(5000))
}
```
