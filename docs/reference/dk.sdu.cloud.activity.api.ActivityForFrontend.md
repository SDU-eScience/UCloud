                            [UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Monitoring and Alerting](/docs/developer-guide/core/monitoring/README.md) / [Activity](/docs/developer-guide/core/monitoring/activity.md)
                            
                            # `ActivityForFrontend`

                            
[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ActivityForFrontend(
    val type: ActivityEventType,
    val timestamp: Long,
    val activityEvent: ActivityEvent,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code><a href='#activityeventtype'>ActivityEventType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>activityEvent</code>: <code><code><a href='#activityevent'>ActivityEvent</a></code></code>
</summary>





</details>



</details>

