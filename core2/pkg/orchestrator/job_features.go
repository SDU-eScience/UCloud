package orchestrator

const (
	jobDockerEnabled   SupportFeatureKey = "jobs.docker.enabled"
	jobDockerWeb       SupportFeatureKey = "jobs.docker.web"
	jobDockerVnc       SupportFeatureKey = "jobs.docker.vnc"
	jobDockerLogs      SupportFeatureKey = "jobs.docker.logs"
	jobDockerTerminal  SupportFeatureKey = "jobs.docker.terminal"
	jobDockerPeers     SupportFeatureKey = "jobs.docker.peers"
	jobDockerExtension SupportFeatureKey = "jobs.docker.extension"

	jobNativeEnabled   SupportFeatureKey = "jobs.native.enabled"
	jobNativeWeb       SupportFeatureKey = "jobs.native.web"
	jobNativeVnc       SupportFeatureKey = "jobs.native.vnc"
	jobNativeLogs      SupportFeatureKey = "jobs.native.logs"
	jobNativeTerminal  SupportFeatureKey = "jobs.native.terminal"
	jobNativePeers     SupportFeatureKey = "jobs.native.peers"
	jobNativeExtension SupportFeatureKey = "jobs.native.extension"

	jobVmEnabled    SupportFeatureKey = "jobs.vm.enabled"
	jobVmWeb        SupportFeatureKey = "jobs.vm.web"
	jobVmVnc        SupportFeatureKey = "jobs.vm.vnc"
	jobVmLogs       SupportFeatureKey = "jobs.vm.logs"
	jobVmTerminal   SupportFeatureKey = "jobs.vm.terminal"
	jobVmPeers      SupportFeatureKey = "jobs.vm.peers"
	jobVmExtension  SupportFeatureKey = "jobs.vm.extension"
	jobVmSuspension SupportFeatureKey = "jobs.vm.suspension"
)

var jobFeatureMapper = []featureMapper{
	{
		Type: jobType,
		Key:  jobDockerEnabled,
		Path: "docker.enabled",
	},
	{
		Type: jobType,
		Key:  jobDockerWeb,
		Path: "docker.web",
	},
	{
		Type: jobType,
		Key:  jobDockerVnc,
		Path: "docker.vnc",
	},
	{
		Type: jobType,
		Key:  jobDockerLogs,
		Path: "docker.logs",
	},
	{
		Type: jobType,
		Key:  jobDockerTerminal,
		Path: "docker.terminal",
	},
	{
		Type: jobType,
		Key:  jobDockerPeers,
		Path: "docker.peers",
	},
	{
		Type: jobType,
		Key:  jobDockerExtension,
		Path: "docker.extension",
	},

	{
		Type: jobType,
		Key:  jobNativeEnabled,
		Path: "native.enabled",
	},
	{
		Type: jobType,
		Key:  jobNativeWeb,
		Path: "native.web",
	},
	{
		Type: jobType,
		Key:  jobNativeVnc,
		Path: "native.vnc",
	},
	{
		Type: jobType,
		Key:  jobNativeLogs,
		Path: "native.logs",
	},
	{
		Type: jobType,
		Key:  jobNativeTerminal,
		Path: "native.terminal",
	},
	{
		Type: jobType,
		Key:  jobNativePeers,
		Path: "native.peers",
	},
	{
		Type: jobType,
		Key:  jobNativeExtension,
		Path: "native.extension",
	},

	{
		Type: jobType,
		Key:  jobVmEnabled,
		Path: "virtualMachine.enabled",
	},
	{
		Type: jobType,
		Key:  jobVmWeb,
		Path: "virtualMachine.web",
	},
	{
		Type: jobType,
		Key:  jobVmVnc,
		Path: "virtualMachine.vnc",
	},
	{
		Type: jobType,
		Key:  jobVmLogs,
		Path: "virtualMachine.logs",
	},
	{
		Type: jobType,
		Key:  jobVmTerminal,
		Path: "virtualMachine.terminal",
	},
	{
		Type: jobType,
		Key:  jobVmPeers,
		Path: "virtualMachine.peers",
	},
	{
		Type: jobType,
		Key:  jobVmExtension,
		Path: "virtualMachine.extension",
	},
	{
		Type: jobType,
		Key:  jobVmSuspension,
		Path: "virtualMachine.suspension",
	},
}
