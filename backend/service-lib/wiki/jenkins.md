For our CI (Continuous Integration), we are using [Jenkins](https://jenkins.io/).

---

## Automated Building and Testing

Our Jenkins [Pipeline](https://jenkins.io/doc/book/pipeline/) is triggered
when new code is pushed to the master branch or staging brach of our git 
repository or a PR from team members is created. Just to
be certain that we don't miss pushes, we also have Jenkins check each 5 min
for changes to to the two branches. If there have been pushed new code to the
repository it triggers our "Build and Test" job. For this job we make use of
[scripted Jenkinsfiles](https://jenkins.io/doc/book/pipeline/jenkinsfile/).
In the base folder of the UCloud project you will find a `Jenkinsfile` that
bootstraps the job. When the bootstrapping `Jenkinsfile` is run, it creates 
an UCloud environment using the UCloud Launcher and runs the integration tests
in a connected VM. Once the tests are done the results of the tests are returned
to Jenkins and in case of errors alerted to the developers.

If the build stage should fail (during compilation) it returns FAILURE. If the test stage fails,
it returns UNSTABLE. If everything is fine and the build and test both are
success it returns SUCCESS. All test results are automatically saved and
gathered in the Jenkins job. 

If 1 or more services has been marked as FAILURE or UNSTABLE, then the job is 
marked as FAILED and a message is sent to our Slack channel `#devalerts` 
specifying which tests are to blame.

![Jenkins Flow Chart](/backend/service-lib/wiki/JenkinsNonParallel.png)



