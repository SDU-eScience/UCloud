package slurm

import (
	"fmt"
	"math/rand"
	"net/http"
	"path/filepath"
	"strings"
	"ucloud.dk/gonja/v2/exec"
	ctrl "ucloud.dk/pkg/im/controller"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func CreateSBatchFile(job *orc.Job, jobFolder string, accountName string) (string, error) {
	application := &job.Status.ResolvedApplication.Invocation
	tool := &job.Status.ResolvedApplication.Invocation.Tool.Tool

	machineConfig, ok := ServiceConfig.Compute.Machines[job.Specification.Product.Category]
	if !ok {
		return "", &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Unknown product requested",
		}
	}

	formattedTimeAllocation := ""
	{
		var timeAllocation orc.SimpleDuration
		if job.Specification.TimeAllocation.IsSet() {
			timeAllocation = job.Specification.TimeAllocation.Get()
		} else {
			timeAllocation = tool.Description.DefaultTimeAllocation
		}

		formattedTimeAllocation += formatTimeNumber(timeAllocation.Hours)
		formattedTimeAllocation += ":"
		formattedTimeAllocation += formatTimeNumber(timeAllocation.Minutes)
		formattedTimeAllocation += ":"
		formattedTimeAllocation += formatTimeNumber(timeAllocation.Seconds)
	}

	parametersAndValues := orc.ReadParameterValuesFromJob(job, application)

	appTemplates := ServiceConfig.Compute.Applications.Templates
	appVars := ServiceConfig.Compute.Applications.Variables

	cli := ""
	invocation := application.Invocation

	argBuilder := orc.DefaultArgBuilder(func(ucloudPath string) string {
		internalPath, _ := UCloudToInternal(ucloudPath)
		return internalPath
	})

	jinjaContextParameters := map[string]any{}

	for k, v := range appVars {
		jinjaContextParameters[k] = v
	}

	for name, pv := range parametersAndValues {
		param := pv.Parameter
		value := pv.Value

		var output any = nil

		switch param.Type {
		case orc.ApplicationParameterTypeTextArea:
			fallthrough
		case orc.ApplicationParameterTypeText:
			output = fmt.Sprint(value.Value)

		case orc.ApplicationParameterTypeFloatingPoint:
			fallthrough
		case orc.ApplicationParameterTypeInteger:
			output = value.Value

		case orc.ApplicationParameterTypeBoolean:
			output = value.Value.(bool)

		case orc.ApplicationParameterTypeEnumeration:
			output = value.Value

		case orc.ApplicationParameterTypeInputFile:
			fallthrough
		case orc.ApplicationParameterTypeInputDirectory:
			internalPath, _ := UCloudToInternal(value.Path)
			output = internalPath

		case orc.ApplicationParameterTypePeer:
			output = value.Hostname

		case orc.ApplicationParameterTypeLicenseServer:
			fallthrough
		case orc.ApplicationParameterTypeNetworkIp:
			fallthrough
		case orc.ApplicationParameterTypeIngress:
			output = value.Id

		default:
			log.Warn("Unhandled value type: %v", param.Type)
		}

		if output != nil {
			jinjaContextParameters[name] = output
		}
	}

	jinjaContextParameters["script"] = func(path string) *exec.Value {
		type OutputStruct struct {
			Output string `json:"output"`
		}

		copiedParameters := map[string]any{}
		for k, v := range jinjaContextParameters {
			if k == "script" {
				continue
			}
			copiedParameters[k] = v
		}

		ext := ctrl.NewScript[any, OutputStruct]()
		ext.Script = path

		res, ok := ext.Invoke(copiedParameters)
		if !ok {
			return exec.AsValue(fmt.Errorf("failed to invoke script: %s", path))
		}
		return exec.AsSafeValue(res.Output)
	}

	allocatedPort := util.Option[int]{}
	if application.ApplicationType == orc.ApplicationTypeWeb || application.ApplicationType == orc.ApplicationTypeVnc {
		allocatedPort.Set(10000 + rand.Intn(40000))
	}

	{
		ucloudCtx := map[string]any{}
		ucloudCtx["jobId"] = job.Id

		{
			product := &job.Status.ResolvedProduct
			machine := map[string]any{}

			machine["name"] = product.Name
			machine["category"] = product.Category.Name

			machine["cpu"] = product.Cpu
			machine["cpuModel"] = product.CpuModel

			machine["memoryInGigs"] = product.MemoryInGigs
			machine["memoryModel"] = product.MemoryModel

			machine["gpu"] = product.Gpu
			machine["gpuModel"] = product.GpuModel

			ucloudCtx["machine"] = machine
		}

		if allocatedPort.Present {
			ucloudCtx["webPort"] = allocatedPort.Value
			ucloudCtx["vncPort"] = allocatedPort.Value
		}

		ucloudCtx["nodes"] = job.Specification.Replicas
		ucloudCtx["partition"] = machineConfig.Partition
		if machineConfig.Qos.Present {
			ucloudCtx["qos"] = machineConfig.Qos.Get()
		}
		{
			app := &job.Status.ResolvedApplication
			appCtx := map[string]any{}

			appCtx["name"] = app.Metadata.Name
			appCtx["version"] = app.Metadata.Version

			ucloudCtx["application"] = appCtx
		}

		jinjaContextParameters["ucloud"] = ucloudCtx
	}

	jinjaContext := exec.NewContext(jinjaContextParameters)

	if len(invocation) == 1 && invocation[0].Type == orc.InvocationParameterTypeJinja {
		output, ok := orc.ExecuteJinjaTemplate(invocation[0].InvocationParameterJinja.Template, appTemplates, jinjaContext, 0)
		if !ok {
			log.Warn("Jinja generation failure for %s %s",
				job.Specification.Application.Name, job.Specification.Application.Version)
			cli = "# Failure during generation of invocation"
		} else {
			cli = output
		}
	} else {
		var cliInvocation []string

		for _, invParam := range invocation {
			newCliArgs := orc.BuildParameter(invParam, parametersAndValues, false, argBuilder, appTemplates, jinjaContext)
			for _, cliArg := range newCliArgs {
				cliInvocation = append(cliInvocation, orc.EscapeBash(cliArg))
			}
		}

		cli = strings.Join(cliInvocation, " ")
	}

	memoryAllocation := ""
	{
		allocInGigs := job.Status.ResolvedProduct.MemoryInGigs
		if allocInGigs <= 0 {
			allocInGigs = 1
		}

		memoryAllocation = fmt.Sprintf("%d", allocInGigs*1000)
	}

	cpuAllocation := job.Status.ResolvedProduct.Cpu
	{
		if cpuAllocation <= 0 {
			cpuAllocation = 1
		}
	}

	devComment := ""
	if ServiceConfig.Compute.FakeResourceAllocation {
		devComment = fmt.Sprintf("# Real CPU = %v, Real mem = %v", cpuAllocation, memoryAllocation)
		cpuAllocation = 1
		memoryAllocation = "50"
	}

	directives := map[string]string{}
	{
		// Pre-defined directives which can be overridden
		// -------------------------------------------------------------------------------------------------------------
		directives["chdir"] = orc.EscapeBash(jobFolder)
		directives["cpus-per-task"] = fmt.Sprint(cpuAllocation)
		directives["mem"] = memoryAllocation
		if job.Status.ResolvedProduct.Gpu != 0 {
			directives["gpus-per-task"] = fmt.Sprint(job.Status.ResolvedProduct.Gpu)
		}
		directives["time"] = formattedTimeAllocation
		directives["nodes"] = fmt.Sprint(job.Specification.Replicas)
		directives["job-name"] = orc.EscapeBash(job.Id)
		directives["output"] = orc.EscapeBash("stdout.txt")
		directives["error"] = orc.EscapeBash("stderr.txt")
		if machineConfig.Qos.IsSet() {
			directives["qos"] = orc.EscapeBash(machineConfig.Qos.Get())
		}

		// Directives from the application
		// -------------------------------------------------------------------------------------------------------------
		for k, v := range application.Sbatch {
			directive := strings.Join(orc.BuildParameter(v, parametersAndValues, false, argBuilder, appTemplates, jinjaContext), " ")
			directive = strings.TrimSpace(directive)
			if directive != "" {
				directive = orc.EscapeBash(directive)
			}
			directives[k] = directive
		}

		// Pre-defined directives which cannot be overridden
		// -------------------------------------------------------------------------------------------------------------
		directives["account"] = orc.EscapeBash(accountName)
		directives["partition"] = orc.EscapeBash(machineConfig.Partition)
		directives["parsable"] = ""
	}

	var modulesToLoad []string
	{
		// Modules come both from the legacy RequiredModules but also from the new load instructions. We start by
		// loading the old modules first followed by the load instructions. In practice, it shouldn't be possible to see
		// both but technically the API supports it.

		for _, module := range tool.Description.RequiredModules {
			modulesToLoad = append(modulesToLoad, module)
		}

		load := tool.Description.LoadInstructions.Get()
		if load.Type == orc.ToolLoadInstructionsNativeModuleWithJinja {
			for _, moduleTemplate := range load.Modules {
				output, ok := orc.ExecuteJinjaTemplate(moduleTemplate, appTemplates, jinjaContext, orc.JinjaFlagsNoEscape)
				if !ok {
					continue
				}

				output = strings.TrimSpace(output)
				modulesToLoad = append(modulesToLoad, output)
			}
		}
	}

	boundEnvironment := map[string]string{}
	{
		for key, param := range application.Environment {
			args := orc.BuildParameter(param, parametersAndValues, true, argBuilder, appTemplates, jinjaContext)
			value := orc.EscapeBash(strings.Join(args, " "))
			boundEnvironment[key] = value
		}

		ucloudCtx := jinjaContextParameters["ucloud"].(map[string]any)
		bindUCloudEnvironmentVariables("UCLOUD", boundEnvironment, ucloudCtx)
	}

	builder := &strings.Builder{}
	{
		appendLine(builder, "#!/usr/bin/env bash")
		for k, v := range directives {
			appendLine(builder, "#SBATCH --%v %v", k, v)
		}
		appendLine(builder, "")

		if devComment != "" {
			appendLine(builder, devComment)
			appendLine(builder, "")
		}

		if allocatedPort.Present {
			appendLine(builder, "export UCLOUD_PORT=%d", allocatedPort.Get())
			appendLine(builder, "echo %d > %v", allocatedPort.Get(), orc.EscapeBash(filepath.Join(jobFolder, AllocatedPortFile)))
			appendLine(builder, "")
		}

		if len(boundEnvironment) > 0 {
			for key, value := range boundEnvironment {
				appendLine(builder, "export %v=%v", key, value)
			}
			appendLine(builder, "")
		}

		if len(modulesToLoad) > 0 {
			appendLine(builder, "module purge")
			for _, module := range modulesToLoad {
				appendLine(builder, "module load %v", orc.EscapeBash(module))
			}
			appendLine(builder, "")
		}

		// TODO constraints

		// Run the actual program
		// ---------------------------------------------------------
		appendLine(builder, cli)
	}

	return builder.String(), nil
}

func bindUCloudEnvironmentVariables(prefix string, environment map[string]string, ctx map[string]any) {
	for k, v := range ctx {
		nested, isNested := v.(map[string]any)
		varName := prefix + "_" + strings.ToUpper(util.ToSnakeCase(k))
		if isNested {
			bindUCloudEnvironmentVariables(varName, environment, nested)
		} else {
			environment[varName] = orc.EscapeBash(fmt.Sprint(v))
		}
	}
}

const AllocatedPortFile = "allocated-port.txt"

func appendLine(builder *strings.Builder, formatString string, args ...any) {
	builder.WriteString(fmt.Sprintf(formatString+"\n", args...))
}

func formatTimeNumber(value int) string {
	if value < 10 {
		return "0" + fmt.Sprint(value)
	} else {
		return fmt.Sprint(value)
	}
}
