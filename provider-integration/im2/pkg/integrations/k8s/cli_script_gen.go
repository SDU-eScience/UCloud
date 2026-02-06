package k8s

import (
	"fmt"
	"os"
	"strconv"

	"gopkg.in/yaml.v3"
	"ucloud.dk/gonja/v2/exec"
	ctrl "ucloud.dk/pkg/controller"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func HandleScriptGen() {
	// NOTE(Dan): Remember to the image reference in invocation.go whenever this function is updated. This function
	// is primarily executed through an init container in the user job.

	// Prepare input
	// -----------------------------------------------------------------------------------------------------------------
	var ourArgs []string
	if len(os.Args) >= 3 {
		ourArgs = os.Args[2:]
	}

	templateFile := util.GetOptionalElement(ourArgs, 0)
	paramsFile := util.GetOptionalElement(ourArgs, 1)
	outputFile := util.GetOptionalElement(ourArgs, 2)
	outputTargetsFile := util.GetOptionalElement(ourArgs, 3)

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

	ucloudRank := os.Getenv("UCLOUD_RANK")
	if ucloudRank != "" {
		rank, err := strconv.ParseInt(ucloudRank, 10, 64)
		if err == nil {
			ucloudValue, ok := jinjaContextParameters["ucloud"]
			if ok {
				ucloudMap, ok := ucloudValue.(map[string]any)
				if ok {
					ucloudMap["rank"] = int(rank)
				}
			}
		}
	}

	// Prepare environment
	// -----------------------------------------------------------------------------------------------------------------
	var targets []orc.DynamicTarget

	jinjaContextParameters["ternary"] = func(condition bool, ifTrue *exec.Value, ifFalse *exec.Value) *exec.Value {
		if condition {
			return ifTrue
		} else {
			return ifFalse
		}
	}

	jinjaContextParameters["dynamicInterface"] = func(rank int, interactiveType string, target string, port int) *exec.Value {
		if rank < 0 {
			return exec.AsSafeValue("\n# Rank must be > 0\n")
		}

		if interactiveType != string(orc.InteractiveSessionTypeVnc) && interactiveType != string(orc.InteractiveSessionTypeWeb) {
			return exec.AsSafeValue("\n# Interactive type must be VNC or WEB\n")
		}

		if port < 0 {
			return exec.AsSafeValue("\n# Port must be > 0\n")
		}

		targets = append(targets, orc.DynamicTarget{
			Rank:   rank,
			Type:   orc.InteractiveSessionType(interactiveType),
			Target: target,
			Port:   port,
		})

		return exec.AsSafeValue("")
	}

	jinjaContextParameters["setInterfaceName"] = func(name string) *exec.Value {
		if name == "" {
			return exec.AsSafeValue("\n echo Name must be entered\n")
		}

		targets = append(targets, orc.DynamicTarget{
			DefaultName: util.OptValue(name),
		})

		return exec.AsSafeValue("")
	}

	// Execute and output
	// -----------------------------------------------------------------------------------------------------------------
	tplSession := &k8sTemplateSession{}
	tpl = ctrl.PreprocessJinjaTemplate(tpl, tplSession, k8sTemplate)

	jinjaContext := exec.NewContext(jinjaContextParameters)
	output, err := ctrl.ExecuteJinjaTemplate(tpl, 0, nil, jinjaContext, ctrl.JinjaFlagsNoPreProcess)
	if err != nil {
		output = fmt.Sprintf(
			"echo %v",
			orc.EscapeBash(fmt.Sprintf("Failure during generation of script: %s", err)),
		)
	}

	output = "#!/usr/bin/env bash\n" + output
	err = os.WriteFile(outputFile.Value, []byte(output), 0777)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to write output file: %s", err)
		os.Exit(1)
	}

	if outputTargetsFile.Present {
		targetsData, err := yaml.Marshal(targets)
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to write targets output file: %s", err)
			os.Exit(1)
		}

		err = os.WriteFile(outputTargetsFile.Value, targetsData, 0777)
		if err != nil {
			termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Failed to write targets output file: %s", err)
			os.Exit(1)
		}
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
