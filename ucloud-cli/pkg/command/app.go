package command

type AppCommand struct {
	Command string // list | search | get
	Args    []string
}

func appHelp() string {
	return "app list | search | get"
}

func (AppCommand) Parse(inputs []string) (Command, bool) {
	//if inputs[0] != "app" {
	//	return nil, false
	//}
	//if len(inputs) > 1 {
	//	// No arguments
	//	return nil, false
	//}
	//// parsing app command
	//switch inputs[1] {
	//case "list", "ls":
	//	return AppCommand{Command: "list", Args: inputs[2:]}, true
	//case "search":
	//	return AppCommand{Command: "search", Args: inputs[2:]}, true
	//case "get":
	//	return AppCommand{Command: "get", Args: inputs[2:]}, true
	//default:
	//	return nil, false
	//
	//}
	return nil, false
}
