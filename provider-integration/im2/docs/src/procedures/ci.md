# GitHub Actions

We use GitHub actions for all of our automatic testing and continuous integration needs.

The workflow automatically triggers a new test run when one of the following things occur:

1. The `master` branch receives a new commit

We implement this pipeline using a GitHub actions workflow. This file exists in the base folder of the UCloud project.
This pipeline creates a UCloud cluster and runs tests against it. We create the cluster using the `./launcher` script.
This creates a functional cluster using all the real software. For example, this includes compute orchestrators such as
Kubernetes and Slurm when relevant. This ensures that the testing environment is as realistic as possible.

A notification is automatically sent to GitHub about the test. We use the results of these tests to determine if we
should deploy a specific build. According to our deployment procedures, it is not a requirement that tests pass. In the
case of a build failure, then the development team is notified via Slack.
