package launcher

func ServiceCliDistribution() {
	service := Service{
		Name:     "cli-distribution",
		Title:    "UCloud CLI Downloads",
		Flags:    SvcLogs | SvcExec,
		UiParent: UiParentCore,
	}

	AddService(service, DockerComposeService{
		Image:    "dreg.cloud.sdu.dk/ucloud/ucloud-cli:" + CliVersion,
		Hostname: "cli-distribution",
		Restart:  "always",
		Networks: pinnedNetwork("172.18.0.20"),
	})
}
