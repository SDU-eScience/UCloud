package command

type SSHKeyCommand struct {
	Command string // list | add | get | delete
	Args    []string
}

func (SSHKeyCommand) Parse() {}
