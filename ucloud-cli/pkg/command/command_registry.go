package command

func CommandTree() map[string]*CommandNode {
	k8s := k8sCommandTree()
	return map[string]*CommandNode{
		"app":             commandRoute("app", AppCommands),
		"workspace":       commandRoute("workspace", WorkspaceCommands),
		"compute":         commandRoute("compute", ComputeCommands),
		"environment":     commandRoute("environment", EnvironmentCommands),
		"ssh-key":         commandRoute("ssh-key", SSHKeyCommands),
		"job":             commandRoute("job", JobCommands),
		"k8s":             k8s,
		"k8s-clusters":    k8s,
		"vm":              commandRoute("vm", VMCommands),
		"connect":         commandRoute("connect", ConnectCommands),
		"public-ip":       commandRoute("public-ip", PublicIPCommands),
		"public-link":     commandRoute("public-link", PublicLinkCommands),
		"private-network": commandRoute("private-network", PrivateNetworkCommands),
		"folder":          commandRoute("folder", FolderCommands),
		"completion":      commandRoute("completion", CompletionCommands),
		"update":          commandRoute("update", UpdateCommands),
		"version":         commandRoute("version", VersionCommands),
		"progress-test":   commandRoute("progress-test", ProgressTestCommands),
		"help":            commandRoute("help", HelpCommands),
	}
}

func commandRoute(name string, commands map[string]CommandFunc) *CommandNode {
	node := commandGroup(map[string]*CommandNode{})
	if factory, ok := commands[name]; ok {
		node.Factory = factory
	}
	for subName, factory := range commands {
		if subName == name {
			continue
		}
		node.Children[subName] = commandLeaf(factory)
	}
	return node
}
