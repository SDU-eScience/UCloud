[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Following the progress of a Job

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A running Job, with ID 123</li>
</ul></td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Jobs.follow.subscribe(
    FindByStringId(
        id = "123", 
    ),
    user,
    handler = { /* will receive messages listed below */ }
)

/*
JobsFollowResponse(
    log = emptyList(), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.IN_QUEUE, 
    ), 
    updates = emptyList(), 
)
*/

/*
JobsFollowResponse(
    log = emptyList(), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.RUNNING, 
    ), 
    updates = listOf(JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.RUNNING, 
        status = "The job is now running", 
        timestamp = 1633680152778, 
    )), 
)
*/

/*
JobsFollowResponse(
    log = listOf(JobsLog(
        rank = 0, 
        stderr = null, 
        stdout = "GNU bash, version 5.0.17(1)-release (x86_64-pc-linux-gnu)" + "\n" + 
            "Copyright (C) 2019 Free Software Foundation, Inc." + "\n" + 
            "License GPLv3+: GNU GPL version 3 or later <http://gnu.org/licenses/gpl.html>" + "\n" + 
            "" + "\n" + 
            "This is free software; you are free to change and redistribute it." + "\n" + 
            "There is NO WARRANTY, to the extent permitted by law.", 
    )), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.RUNNING, 
    ), 
    updates = emptyList(), 
)
*/

/*
JobsFollowResponse(
    log = emptyList(), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.SUCCESS, 
    ), 
    updates = listOf(JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.SUCCESS, 
        status = "The job is no longer running", 
        timestamp = 1633680152778, 
    )), 
)
*/

```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_follow.png)

</details>


