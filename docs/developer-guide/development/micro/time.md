<p align='center'>
<a href='/docs/developer-guide/development/micro/cache.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/types.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Developing UCloud](/docs/developer-guide/development/README.md) / [Micro Library Reference](/docs/developer-guide/development/micro/README.md) / Time
# Time

UCloud provides a `Time` object which should be used for all actions which require a timestamp. The timestamps used in
UCloud are always returned as time expressed as milliseconds since the time 00:00:00 UTC on January 1, 1970.

__Example:__ Retrieving the current time

```kotlin
val timestamp = Time.now()
```

## `object Time : TimeProvider`

__Properties:__

```kotlin
var provider: TimeProvider = SystemTimeProvider
```

Changing the `provider` allows you to mock the time. This is extremely useful for testing time-releated code. You
should _never_ change the `provider` in UCloud production code.

__Member functions:__

```kotlin
fun TimeProvider.now(): Long
```

Returns the current time expressed as milliseconds since the time 00:00:00 UTC on January 1, 1970.
See also [System.currentTimeMillis](https://docs.oracle.com/javase/7/docs/api/java/lang/System.html#currentTimeMillis()).

## `object SystemTimeProvider : TimeProvider`

Provides the default time provider. This delegates to `System.currentTimeMillis()`.

## `object StaticTimeProvider : TimeProvider`

Provides a `TimeProvider` which has a static value which can be changed by the user.

__Properties:__

```kotlin
var time: Long = 0L
```

The `time` property will be returned every time some code calls `StaticTimeProvider.now()`. No synchronization is done
on this property.
