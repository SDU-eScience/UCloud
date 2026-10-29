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

func CommandTree() map[string]*CommandNode {
	registry := CommandRegistry()
	tree := make(map[string]*CommandNode, len(registry))
	for name, route := range registry {
		node := commandGroup(map[string]*CommandNode{})
		if factory, ok := route[name]; ok {
			node.Factory = factory
		}
		for subName, factory := range route {
			if subName == name {
				continue
			}
			node.Children[subName] = commandLeaf(factory)
		}
		tree[name] = node
	}

	maintenance := k8sMaintenanceCommandGroup()
	for _, name := range []string{"k8s", "k8s-clusters"} {
		node, ok := tree[name]
		if !ok {
			continue
		}
		node.Children["maintenance"] = maintenance
	}
	return tree
}
