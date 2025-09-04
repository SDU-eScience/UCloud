package orchestrator

const (
	jobDockerEnabled   featureKey = "jobs.docker.enabled"
	jobDockerWeb       featureKey = "jobs.docker.web"
	jobDockerVnc       featureKey = "jobs.docker.vnc"
	jobDockerLogs      featureKey = "jobs.docker.logs"
	jobDockerTerminal  featureKey = "jobs.docker.terminal"
	jobDockerPeers     featureKey = "jobs.docker.peers"
	jobDockerExtension featureKey = "jobs.docker.extension"

	jobNativeEnabled   featureKey = "jobs.native.enabled"
	jobNativeWeb       featureKey = "jobs.native.web"
	jobNativeVnc       featureKey = "jobs.native.vnc"
	jobNativeLogs      featureKey = "jobs.native.logs"
	jobNativeTerminal  featureKey = "jobs.native.terminal"
	jobNativePeers     featureKey = "jobs.native.peers"
	jobNativeExtension featureKey = "jobs.native.extension"

	jobVmEnabled    featureKey = "jobs.vm.enabled"
	jobVmWeb        featureKey = "jobs.vm.web"
	jobVmVnc        featureKey = "jobs.vm.vnc"
	jobVmLogs       featureKey = "jobs.vm.logs"
	jobVmTerminal   featureKey = "jobs.vm.terminal"
	jobVmPeers      featureKey = "jobs.vm.peers"
	jobVmExtension  featureKey = "jobs.vm.extension"
	jobVmSuspension featureKey = "jobs.vm.suspension"
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
