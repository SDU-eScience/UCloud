package k8s

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"os"
	"ucloud.dk/gonja/v2/exec"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

func HandleScriptGen() {
	// Prepare input
	// -----------------------------------------------------------------------------------------------------------------
	var ourArgs []string
	if len(os.Args) >= 3 {
		ourArgs = os.Args[2:]
	}

	templateFile := util.GetOptionalElement(ourArgs, 0)
	paramsFile := util.GetOptionalElement(ourArgs, 1)
	outputFile := util.GetOptionalElement(ourArgs, 2)

	if !templateFile.Present || !paramsFile.Present || !outputFile.Present {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Usage: ucloud script-gen <templateFile> <paramsFile> <outputFile>")
		os.Exit(1)
	}

	templateData, err := os.ReadFile(templateFile.Value)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Could not read template file: %s", err)
		os.Exit(1)
	}
	tpl := string(templateData)

	paramsData, err := os.ReadFile(paramsFile.Value)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Could not read params file: %s", err)
		os.Exit(1)
	}

	jinjaContextParameters := map[string]any{}
	err = yaml.Unmarshal(paramsData, &jinjaContextParameters)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Could not read params file: %s", err)
		os.Exit(1)
	}

	// Prepare environment
	// -----------------------------------------------------------------------------------------------------------------
	jinjaContextParameters["ternary"] = func(condition bool, ifTrue *exec.Value, ifFalse *exec.Value) *exec.Value {
		if condition {
			return ifTrue
		} else {
			return ifFalse
		}
	}

	// Execute and output
	// -----------------------------------------------------------------------------------------------------------------
	tplSession := &k8sTemplateSession{}
	tpl = orc.PreprocessJinjaTemplate(tpl, tplSession, k8sTemplate)

	jinjaContext := exec.NewContext(jinjaContextParameters)
	output, err := orc.ExecuteJinjaTemplate(tpl, 0, nil, jinjaContext, orc.JinjaFlagsNoPreProcess)
	if err != nil {
		output = fmt.Sprintf(
			"echo %v",
			orc.EscapeBash(fmt.Sprintf("Failure during generation of script: %s", err)),
		)
	}

	output = "#!/usr/bin/env bash\n" + output
	err = os.WriteFile(outputFile.Value, []byte(output), 0770)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to write output file: %s", err)
		os.Exit(1)
	}
}

type k8sTemplateSession struct {
	Error error
}

func k8sTemplate(session any, fn string, args []string) string {
	templateSession := session.(*k8sTemplateSession)
	switch fn {
	default:
		templateSession.Error = fmt.Errorf("unknown function %s", fn)
		return fmt.Sprintf("\n# %s\n", templateSession.Error)
	}
}
