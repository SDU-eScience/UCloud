package builtins

import (
	methods "ucloud.dk/gonja/v2/builtins/methods"
	"ucloud.dk/gonja/v2/exec"
)

// ControlStructures exports all builtins controlStructures
var Methods exec.Methods = methods.All
