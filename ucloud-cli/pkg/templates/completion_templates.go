package templates

import _ "embed"

//go:embed zsh_completion.j2
var TplZshCompletion []byte
