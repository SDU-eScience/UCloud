package command

type ConnectCommand struct {
	Token  string
	Server string
}

func (ConnectCommand) Parse() {}
