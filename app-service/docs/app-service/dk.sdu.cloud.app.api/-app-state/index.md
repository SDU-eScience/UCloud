[app-service](../../index.md) / [dk.sdu.cloud.app.api](../index.md) / [AppState](./index.md)

# AppState

`enum class AppState`

### Enum Values

| Name | Summary |
|---|---|
| [VALIDATED](-v-a-l-i-d-a-t-e-d.md) | The job has been validated and is ready to be processed for scheduling |
| [PREPARED](-p-r-e-p-a-r-e-d.md) | The job has all of its dependencies shipped to HPC and is ready to be scheduled |
| [SCHEDULED](-s-c-h-e-d-u-l-e-d.md) | The job has been scheduled in Slurm |
| [RUNNING](-r-u-n-n-i-n-g.md) | The job is currently running in the HPC environment |
| [SUCCESS](-s-u-c-c-e-s-s.md) | The job has completed succesfully |
| [FAILURE](-f-a-i-l-u-r-e.md) | The job has completed unsuccessfully |

### Functions

| Name | Summary |
|---|---|
| [isFinal](is-final.md) | `fun isFinal(): `[`Boolean`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/index.html) |
