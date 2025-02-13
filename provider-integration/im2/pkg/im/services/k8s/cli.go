package k8s

func HandleCliWithoutConfig(command string) bool {
	switch command {
	case "script-gen":
		HandleScriptGen()
	default:
		return false
	}
	return true
}
