package command

type WorkspaceCommand struct {
	Verb  string // list | use | get | delete | rename
	Args  []string
	Flags map[string]string // --url
}

func (WorkspaceCommand) Parse() {}
