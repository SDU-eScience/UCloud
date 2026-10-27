package command

func CommandRegistry() map[string]map[string]CommandFunc {
	registry := map[string]map[string]CommandFunc{}
	registry["app"] = AppCommands
	registry["workspace"] = WorkspaceCommands
	registry["compute"] = ComputeCommands
	registry["environment"] = EnvironmentCommands
	registry["ssh-key"] = SSHKeyCommands
	registry["job"] = JobCommands
	registry["k8s"] = K8sClusterCommands
	registry["k8s-clusters"] = K8sClusterCommands
	registry["vm"] = VMCommands
	registry["connect"] = ConnectCommands
	registry["public-ip"] = PublicIPCommands
	registry["public-link"] = PublicLinkCommands
	registry["private-network"] = PrivateNetworkCommands
	registry["folder"] = FolderCommands
	registry["completion"] = CompletionCommands
	registry["update"] = UpdateCommands
	registry["version"] = VersionCommands
	registry["progress-test"] = ProgressTestCommands
	registry["help"] = HelpCommands
	return registry
}
