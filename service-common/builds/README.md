# Builds

This directory contains shallow JAR builds for this project. This is a bit
of a temporary meassure, ideally we would have tighter integration with
Gradle.

Since it is a shallow JAR you will have to add the dependencies of this project
yourself in your own `build.gradle`.

You may perform a build with `gradle jar` and then copy the output artifact
from `build/libs`. Remember to do a version bump before running `gradle jar`.
For these projects we will be using semver.

To add one of these as a dependency in your project you will have to add:

```groovy
dependencies {
    compile files(service)
}
```

Along with this you will have to add the dependencies of the specific version.

