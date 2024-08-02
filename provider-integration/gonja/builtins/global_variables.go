package builtins

import "ucloud.dk/gonja/v2/exec"

var GlobalVariables = exec.NewContext(map[string]interface{}{
	"gonja": map[string]interface{}{
		"version": "v2.3.0",
	},
})
