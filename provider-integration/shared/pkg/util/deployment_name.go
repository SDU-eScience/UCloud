package util

// DeploymentName is used by monitoring tools to determine what the source of a given event is. This should be done
// roughly at the K8s deployment level. For example: Core-Foundation or IM2. This value is expected to be set exactly
// once early in the startup process. Ideally, this is set as one of the first things in the cmd package.
var DeploymentName = "untitled-script"
