[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `jobs.retrieveUtilization`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve information about how busy the provider's cluster currently is_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#jobsretrieveutilizationrequest'>JobsRetrieveUtilizationRequest</a></code>|<code><a href='#jobsretrieveutilizationresponse'>JobsRetrieveUtilizationResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will return information about how busy a cluster is. This endpoint is only used for
informational purposes. UCloud does not use this information for any accounting purposes.


