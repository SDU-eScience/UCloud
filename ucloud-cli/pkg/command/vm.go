package command

var validVMFlags = map[string]struct{}{
	"image":           {},
	"product":         {},
	"ssh":             {},
	"public-ip":       {},
	"private-network": {},
}

type VMCommand struct {
	Verb  string // list | get | delete | stop | shell
	Args  []string
	Flags map[string]string
}

func (VMCommand) Parse() {}
