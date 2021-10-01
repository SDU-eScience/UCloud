# `JobState`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


_A value describing the current state of a Job_

```kotlin
enum class JobState {
    IN_QUEUE,
    RUNNING,
    CANCELING,
    SUCCESS,
    FAILURE,
    EXPIRED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>IN_QUEUE</code> Any job which has been submitted and not yet in a final state where the number of tasks running is less thanthe number of tasks requested
</summary>





</details>

<details>
<summary>
<code>RUNNING</code> A job where all the tasks are running
</summary>





</details>

<details>
<summary>
<code>CANCELING</code> A job which has been cancelled, either by user request or system request
</summary>





</details>

<details>
<summary>
<code>SUCCESS</code> A job which has terminated. The job terminated with no _scheduler_ error.
</summary>



Note: A job will complete successfully even if the user application exits with an unsuccessful status code.


</details>

<details>
<summary>
<code>FAILURE</code> A job which has terminated with a failure.
</summary>



Note: A job will fail _only_ if it is the scheduler's fault


</details>

<details>
<summary>
<code>EXPIRED</code> A job which has expired and was terminated as a result
</summary>





</details>



</details>

