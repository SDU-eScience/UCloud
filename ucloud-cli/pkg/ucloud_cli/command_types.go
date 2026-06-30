package ucloud_cli

type ResourceCommand struct {
	Resource    string
	Subresource string
	Verb        string

	Target string
	Flags  map[string]string
}

func (ResourceCommand) isCommand() {}
