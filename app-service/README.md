# Application Service `app-service`

| Port 42200 | [Technical Documentation](docs/app-service/index.md) | 
[API Documentation](docs/app-service/dk.sdu.cloud.app.api/index.md) |

This service is responsible for implementing the 'application' feature of SDUCloud.

## Overview

The 'application' feature allows for end-users of SDUCloud to run containerized
applications on cloud HPC (currently ABC2.0 is the only backend supported).


```
  +-------------+    +--------------+    +-------------+
  | app_serv    |--->| ssh_client   |--->| ABC2.0      |
  +-------------+    +--------------+    +-------------+
                                               |
                                               v
                                         +-------------+
                                         | slurm       |
                                         +-------------+
                                               |
                                               v
                                       +-----------------+
                                       | containers      |
                                       +-----------------+
                                         |             |
                                         v             v
                                +----------+         +-------------+
                                | udocker  |         | singularity |
                                +----------+         +-------------+

```

__Figure:__ Overall architecture of `app-service`

## How to Run

This service follows the SDUCloud standard and runs on port 42200. Distributions can be
made with the gradle `application` plugin (already configured). Following this the service
will correctly respond to HTTP requests.

### Making a Request

The following will make an example for the 
[list recent jobs](docs/app-service/dk.sdu.cloud.app.api/-h-p-c-job-descriptions/list-recent.md) operation.

```
$ http :42200/api/hpc/jobs?page=0\&itemsPerPage=10 "Authorization: Bearer ${JWT_TOKEN}" "Job-Id: `uuidgen`"

HTTP/1.1 200 OK
Content-Length: 2285
Content-Type: application/json; charset=UTF-8
Date: Wed, 09 May 2018 13:14:38 GMT
Server: ktor-server-core/0.9.1 ktor-server-core/0.9.1

{
    "items": [
        {
            "appName": "figlet-count",
            "appVersion": "1.0.0",
            "createdAt": 1525869327282,
            "jobId": "fb4dad6a-eaf9-42b1-b88c-f6c7249cd1d1",
            "modifiedAt": 1525869381246,
            "owner": "jonas@hinchely.dk",
            "state": "SUCCESS",
            "status": "OK"
        },
        {
            "appName": "figlet-count",
            "appVersion": "1.0.0",
            "createdAt": 1525869175857,
            "jobId": "79c6615c-c311-40ff-abc9-de00bc7a8035",
            "modifiedAt": 1525869209498,
            "owner": "jonas@hinchely.dk",
            "state": "SUCCESS",
            "status": "OK"
        }
    ],
    "itemsInTotal": 2,
    "itemsPerPage": 10,
    "pageNumber": 0,
    "pagesInTotal": 1
}

```

## Configuration

Click [here](docs/app-service/dk.sdu.cloud.app/-h-p-c-config/index.md) for more
information.

