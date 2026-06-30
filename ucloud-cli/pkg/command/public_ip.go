package command

var validFlags = map[string]struct{}{
	"open-port": {}, "product": {},
}

type PublicIPCommand struct {
	Command string // list | get | delete | firewall
	Args    []string
	Flags   map[string]string
}

func (PublicIPCommand) Parse() {}
