package command

type PublicLinkCommand struct {
	Verb     string
	Resource string
}

func (PublicLinkCommand) Parse() {}
