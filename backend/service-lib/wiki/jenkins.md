We use Jenkins for all of our automatic testing and continuous integration needs.

Jenkins automatically triggers a new test run when one of the following things occur:

1. Either the `master` or `staging` branch receives a new commit
2. A member of the UCloud team creates a pull request on GitHub

We implement this pipeline using a scripted Jenkinsfile. This file exists in the base folder of the UCloud project. This
pipeline creates a UCloud cluster and runs tests against it. We create the cluster using the `./launcher` script. This
creates a functional cluster using all the real software. For example, this includes compute orchestrators such as
Kubernetes and Slurm when relevant. This ensures that the testing environment is as realistic as possible.

Jenkins run these test on a separate agent. This agent runs in an isolated virtual machine to minimise the risk. This
agent has severely limited connectivity to other infrastructure.

A notification is automatically sent to GitHub about the test. We use the results of these tests to determine if we
should deploy a specific build. According to our deployment procedures, it is not a requirement that tests pass. In the
case of a build failure, then the development team is notified via Slack.
