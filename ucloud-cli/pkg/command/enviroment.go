package command

type EnvironmentCommand struct {
	Verb string // list | add | use
	Name string
	URL  string
}
