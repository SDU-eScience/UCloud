[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Outgoing API](/docs/developer-guide/orchestration/compute/providers/jobs/outgoing.md)

# `SSHKey`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A public key belonging to a UCloud user intended for SSH purposes_

```kotlin
data class SSHKey(
    val id: String,
    val owner: String,
    val createdAt: Long,
    val fingerprint: String,
    val specification: SSHKey.Spec,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> An opaque and unique identifier representing this SSH key
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The UCloud username of the user who owns this key
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp for when this key was created in UCloud
</summary>





</details>

<details>
<summary>
<code>fingerprint</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A fingerprint of the key
</summary>



This is used to aid end-users identify the key more easily. The fingerprint will, in most cases, contain a
cryptographic hash along with any additional comments the key might have associated with it. The format of this
property is not stable.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#sshkey.spec'>SSHKey.Spec</a></code></code> Contains the user-specified part of the key
</summary>





</details>



</details>


