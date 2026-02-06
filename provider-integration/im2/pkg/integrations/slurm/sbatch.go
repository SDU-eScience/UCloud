package slurm

import (
	"bytes"
	"fmt"
	"math/rand"
	"net/http"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"unicode"

	"gopkg.in/yaml.v3"
	"ucloud.dk/gonja/v2/exec"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

type appCfgAndVersion struct {
	Version string
	Cfg     cfg.SlurmApplicationConfiguration
}

type sbatchTemplateSession struct {
	Applications         map[string][]cfg.SlurmApplicationConfiguration
	RequiredApplications []orc.NativeApplication
	VersionPolicy        string
	VersionTarget        util.Option[string]
	Error                error
	PreviouslyLoaded     map[orc.NativeApplication]appCfgAndVersion
	SrunOverride         util.Option[cfg.SrunConfiguration]
	ParametersAndValues  map[string]controller.ParamAndValue
	Parameters           map[string]orc.AppParameterValue
}

func (s *sbatchTemplateSession) compareVersions(a, b string, missingComponentIsEquality bool) int {
	a = strings.ReplaceAll(a, "version", "")
	a = strings.ReplaceAll(a, "Version", "")

	b = strings.ReplaceAll(b, "version", "")
	b = strings.ReplaceAll(b, "Version", "")

	if len(a) >= 2 && a[0] == 'v' && unicode.IsDigit(rune(a[1])) {
		a = a[1:]
	}

	if len(b) >= 2 && b[0] == 'v' && unicode.IsDigit(rune(b[1])) {
		b = b[1:]
	}

	a = strings.TrimSpace(a)
	b = strings.TrimSpace(b)

	aTokens := strings.Split(a, ".")
	bTokens := strings.Split(b, ".")

	maxLen := max(len(aTokens), len(bTokens))
	for i := 0; i < maxLen; i++ {
		aTok := ""
		bTok := ""

		if i < len(aTokens) {
			aTok = aTokens[i]
		} else {
			aTok = "0"
			if missingComponentIsEquality {
				break
			}
		}

		if i < len(bTokens) {
			bTok = bTokens[i]
		} else {
			bTok = "0"
			if missingComponentIsEquality {
				break
			}
		}

		aSepIdx := strings.Index(aTok, "-")
		if aSepIdx != -1 {
			aTok = aTok[:aSepIdx-1]
		}

		bSepIdx := strings.Index(bTok, "-")
		if bSepIdx != -1 {
			bTok = bTok[:bSepIdx-1]
		}

		aNumeric, err1 := strconv.Atoi(aTok)
		bNumeric, err2 := strconv.Atoi(bTok)

		if err1 == nil && err2 == nil {
			if aNumeric > bNumeric {
				return 1
			} else if aNumeric < bNumeric {
				return -1
			}
		} else {
			cmp := strings.Compare(aTok, bTok)
			if cmp != 0 {
				return cmp
			}
		}
	}

	return 0
}

func (s *sbatchTemplateSession) findConfig(version string, configs []cfg.SlurmApplicationConfiguration) cfg.SlurmApplicationConfiguration {
	for _, c := range configs {
		for _, v := range c.Versions {
			if version == v {
				return c
			}
		}
	}

	return cfg.SlurmApplicationConfiguration{}
}

func (s *sbatchTemplateSession) FindApplication(name, version string) (cfg.SlurmApplicationConfiguration, string, bool) {
	key := orc.NativeApplication{Name: name, Version: version}
	existing, ok := s.PreviouslyLoaded[key]
	if ok {
		return existing.Cfg, existing.Version, true
	}

	res, resVersion, ok := (func() (cfg.SlurmApplicationConfiguration, string, bool) {
		apps, ok := s.Applications[name]
		if !ok {
			return cfg.SlurmApplicationConfiguration{}, "", false
		}

		var allVersions []string
		for _, app := range apps {
			for _, thisVersion := range app.Versions {
				allVersions = append(allVersions, thisVersion)
			}
		}

		slices.SortFunc(allVersions, func(a, b string) int {
			cmp := s.compareVersions(a, b, false)
			if cmp > 0 {
				return -1
			} else if cmp < 0 {
				return 1
			} else {
				return 0
			}
		})

		if len(allVersions) == 0 {
			return cfg.SlurmApplicationConfiguration{}, "", false
		}

		versionPolicy := s.VersionPolicy
		if s.VersionTarget.Present && s.VersionTarget.Value != name {
			versionPolicy = "loose"
		}

		switch versionPolicy {
		case "loose":
			for _, candidate := range allVersions {
				if s.compareVersions(version, candidate, true) == 0 {
					return s.findConfig(candidate, apps), candidate, true
				}
			}

			return s.findConfig(allVersions[0], apps), allVersions[0], true

		case "loose-forward":
			for _, candidate := range allVersions {
				if s.compareVersions(version, candidate, true) == 0 {
					return s.findConfig(candidate, apps), candidate, true
				}
			}
			candidate := allVersions[0]
			if s.compareVersions(candidate, version, true) < 0 {
				return cfg.SlurmApplicationConfiguration{}, "", false
			} else {
				return s.findConfig(candidate, apps), candidate, true
			}

		case "loose-backward":
			for _, candidate := range allVersions {
				if s.compareVersions(version, candidate, true) == 0 {
					return s.findConfig(candidate, apps), candidate, true
				}
			}

			for _, candidate := range allVersions {
				if s.compareVersions(candidate, version, false) <= 0 {
					return s.findConfig(candidate, apps), candidate, true
				}
			}
			return cfg.SlurmApplicationConfiguration{}, "", false

		case "strict":
			for _, candidate := range allVersions {
				if s.compareVersions(version, candidate, true) == 0 {
					return s.findConfig(candidate, apps), candidate, true
				}
			}
			return cfg.SlurmApplicationConfiguration{}, "", false

		default:
			for _, candidate := range allVersions {
				if candidate == version {
					return s.findConfig(candidate, apps), candidate, true
				}
			}

			return cfg.SlurmApplicationConfiguration{}, "", false
		}
	})()

	if ok {
		s.PreviouslyLoaded[key] = appCfgAndVersion{Cfg: res, Version: resVersion}
		if res.Srun.Present && !s.SrunOverride.Present {
			s.SrunOverride.Set(res.Srun.Value)
		}
	}

	return res, resVersion, ok
}

func (s *sbatchTemplateSession) LoadApplication(name string, version string) string {
	if s.Error != nil {
		return ""
	}

	appCfg, appVersion, ok := s.FindApplication(name, version)
	if !ok {
		s.Error = fmt.Errorf("failed to load application %s@%s", name, version)
		return fmt.Sprintf("\necho '%s'\nexit 1\n", s.Error)
	}

	builder := ""
	builder += "{% set __appVersionOld = appVersion %}\n"
	builder += "{% set appVersion = \"" + appVersion + "\" %}\n"
	builder += appCfg.Load
	builder += "\n"
	builder += "{% set appVersion = __appVersionOld %}\n"

	return builder
}

func (s *sbatchTemplateSession) UnloadApplication(name string, version string) string {
	if s.Error != nil {
		return ""
	}

	appCfg, appVersion, ok := s.FindApplication(name, version)
	if !ok {
		s.Error = fmt.Errorf("failed to load application '%s'@'%s'", name, version)
		return fmt.Sprintf("\n# %s\n", s.Error)
	}

	builder := ""
	builder += "{% set __appVersionOld = appVersion %}\n"
	builder += "{% set appVersion = \"" + appVersion + "\" %}\n"
	builder += appCfg.Unload
	builder += "\n"
	builder += "{% set appVersion = __appVersionOld %}\n"

	return builder
}

func sbatchTemplate(session any, fn string, args []string) string {
	templateSession := session.(*sbatchTemplateSession)

	switch fn {
	case "versionResolver":
		target := util.Option[string]{}
		policy := ""
		if len(args) == 1 {
			policy = args[0]
		} else if len(args) == 2 {
			target.Set(args[0])
			policy = args[1]
		} else {
			templateSession.Error = fmt.Errorf("invalid use of versionResolver([name], policy)")
			return fmt.Sprintf("\n# %s\n", templateSession.Error)
		}

		templateSession.VersionTarget = target
		templateSession.VersionPolicy = policy
		return ""

	case "applicationLoad":
		result := ""
		for _, app := range templateSession.RequiredApplications {
			result += sbatchTemplate(session, "loadApplication", []string{app.Name, app.Version})
			result += "\n"
		}
		return result

	case "applicationUnload":
		result := ""
		for _, app := range templateSession.RequiredApplications {
			result += sbatchTemplate(session, "unloadApplication", []string{app.Name, app.Version})
			result += "\n"
		}
		return result

	case "loadApplication":
		if len(args) != 2 {
			templateSession.Error = fmt.Errorf("invalid use of loadApplication(name, version)")
			return fmt.Sprintf("\n# %s\n", templateSession.Error)
		}

		return templateSession.LoadApplication(args[0], args[1])

	case "unloadApplication":
		if len(args) != 2 {
			templateSession.Error = fmt.Errorf("invalid use of unloadApplication(name, version)")
			return fmt.Sprintf("\n# %s\n", templateSession.Error)
		}

		return templateSession.UnloadApplication(args[0], args[1])

	case "systemLoad":
		sysLoad := ServiceConfig.Compute.SystemLoadCommand.GetOrDefault("")
		builder := sysLoad + "\n"

		modulesToLoad, ok := templateSession.Parameters[SlurmModulesParameter]
		if ok {
			for _, m := range modulesToLoad.Modules {
				builder += fmt.Sprintf("module load %s\n", orc.EscapeBash(m))
			}
		}

		return builder

	case "systemUnload":
		return ServiceConfig.Compute.SystemUnloadCommand.GetOrDefault("")

	default:
		templateSession.Error = fmt.Errorf("unknown function %s", fn)
		return fmt.Sprintf("\n# %s\n", templateSession.Error)
	}
}

func prepareDefaultEnvironment(
	job *orc.Job,
	jobFolder string,
	accountName string,
	parametersAndValues map[string]controller.ParamAndValue,
	argBuilder controller.ArgBuilder,
	allocatedPort util.Option[int],
) (directives map[string]string, jinjaContextParameters map[string]any, targets *[]orc.DynamicTarget) {
	directives = make(map[string]string)
	jinjaContextParameters = make(map[string]any)

	targets = &[]orc.DynamicTarget{} // needed to avoid a nil dereference

	// SBatch directives
	// =================================================================================================================
	application := &job.Status.ResolvedApplication.Value.Invocation
	tool := &job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value
	machineConfig, _ := ServiceConfig.Compute.Machines[job.Specification.Product.Category]

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

	memoryAllocation := ""
	resolvedProduct := job.Status.ResolvedProduct.Value
	{
		allocInGigs := resolvedProduct.MemoryInGigs
		if allocInGigs <= 0 {
			allocInGigs = 1
		}

		memoryAllocation = fmt.Sprintf("%d", allocInGigs*1000)
	}

	cpuAllocation := resolvedProduct.Cpu
	{
		if cpuAllocation <= 0 {
			cpuAllocation = 1
		}
	}

	if ServiceConfig.Compute.FakeResourceAllocation {
		cpuAllocation = 1
		memoryAllocation = "50"
	}

	{
		// Pre-defined directives which can be overridden
		// -------------------------------------------------------------------------------------------------------------
		directives["chdir"] = orc.EscapeBash(jobFolder)
		directives["cpus-per-task"] = fmt.Sprint(cpuAllocation)
		directives["mem"] = memoryAllocation
		if resolvedProduct.Gpu != 0 {
			directives["gpus-per-task"] = fmt.Sprint(resolvedProduct.Gpu)
		}
		directives["time"] = formattedTimeAllocation
		directives["nodes"] = fmt.Sprint(job.Specification.Replicas)
		directives["job-name"] = orc.EscapeBash(job.Id)
		directives["output"] = orc.EscapeBash("stdout.txt")
		directives["error"] = orc.EscapeBash("stderr.txt")
		if machineConfig.Qos.IsSet() {
			directives["qos"] = orc.EscapeBash(machineConfig.Qos.Get())
		}

		// Pre-defined directives which cannot be overridden
		// -------------------------------------------------------------------------------------------------------------
		directives["account"] = orc.EscapeBash(accountName)
		directives["partition"] = orc.EscapeBash(machineConfig.Partition)
		directives["parsable"] = ""
		directives["comment"] = orc.EscapeBash(ucloudSlurmComment)
	}

	// Jinja context
	// =================================================================================================================
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

		case orc.ApplicationParameterTypeWorkflow:
			output = nil

		default:
			log.Warn("Unhandled value type: %v", param.Type)
		}

		if output != nil {
			jinjaContextParameters[name] = output
		}
	}

	{
		ucloudCtx := map[string]any{}
		ucloudCtx["jobId"] = job.Id

		{
			product := &resolvedProduct
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
			app := &job.Status.ResolvedApplication.Value
			appCtx := map[string]any{}

			appCtx["name"] = app.Metadata.Name
			appCtx["version"] = app.Metadata.Version

			ucloudCtx["application"] = appCtx
		}

		jinjaContextParameters["ucloud"] = ucloudCtx
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

		ext := controller.NewScript[any, OutputStruct]()
		ext.Script = path

		res, ok := ext.Invoke(copiedParameters)
		if !ok {
			return exec.AsValue(fmt.Errorf("failed to invoke script: %s", path))
		}
		return exec.AsSafeValue(res.Output)
	}

	jinjaContextParameters["sbatch"] = func(param string, value any) *exec.Value {
		if param == "gpus-per-task" {
			if f, ok := value.(float64); ok && f <= 0 {
				return exec.AsSafeValue("")
			}
		}

		if !slices.Contains(directivesWhichCannotBeChanged, param) {
			directives[param] = fmt.Sprint(value)
		}

		return exec.AsSafeValue("")
	}

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

		*targets = append(*targets, orc.DynamicTarget{
			Rank:   rank,
			Type:   orc.InteractiveSessionType(interactiveType),
			Target: target,
			Port:   port,
		})

		return exec.AsSafeValue("")
	}

	// Directives from the application
	// -------------------------------------------------------------------------------------------------------------
	// These need a Jinja context available so we do it quite a bit later

	jinjaContext := exec.NewContext(jinjaContextParameters)
	for k, v := range application.Sbatch {
		if !slices.Contains(directivesWhichCannotBeChanged, k) {
			directive := strings.Join(controller.BuildParameter(v, parametersAndValues, false, argBuilder, jinjaContext), " ")
			directive = strings.TrimSpace(directive)
			if directive != "" {
				directive = orc.EscapeBash(directive)
			}

			directives[k] = directive
		}
	}

	return
}

type SBatchResult struct {
	Content             string
	JinjaTemplateFile   string
	JinjaParametersFile string
	Error               error
	DynamicTargets      []orc.DynamicTarget
}

func CreateSBatchFile(job *orc.Job, jobFolder string, accountName string) SBatchResult {
	application := &job.Status.ResolvedApplication.Value.Invocation
	tool := &job.Status.ResolvedApplication.Value.Invocation.Tool.Tool.Value
	jinjaTemplateFile := ""
	jinjaParametersFile := ""

	_, ok := ServiceConfig.Compute.Machines[job.Specification.Product.Category]
	if !ok {
		return SBatchResult{
			Error: (&util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        "Unknown product requested",
			}).AsError(),
		}
	}

	parametersAndValues := controller.ReadParameterValuesFromJob(job, application)
	cli := ""
	invocation := application.Invocation

	argBuilder := controller.DefaultArgBuilder(func(ucloudPath string) string {
		internalPath, _ := UCloudToInternal(ucloudPath)
		return internalPath
	})

	// Convert license parameters
	for parameterId, parameterAndValue := range parametersAndValues {
		if parameterAndValue.Parameter.Type == orc.ApplicationParameterTypeLicenseServer {
			newParameterAndValue := parameterAndValue
			newParameterAndValue.Value.Id = controller.BuildLicenseParameter(parameterAndValue.Value.Id)
			parametersAndValues[parameterId] = newParameterAndValue
		}
	}

	allocatedPort := util.Option[int]{}
	if application.ApplicationType == orc.ApplicationTypeWeb || application.ApplicationType == orc.ApplicationTypeVnc {
		allocatedPort.Set(10000 + rand.Intn(40000))
	}

	directives, jinjaContextParameters, targets := prepareDefaultEnvironment(
		job,
		jobFolder,
		accountName,
		parametersAndValues,
		argBuilder,
		allocatedPort,
	)

	var jinjaContext *exec.Context = nil

	if len(invocation) == 1 && invocation[0].Type == orc.InvocationParameterTypeJinja {
		tpl := invocation[0].InvocationParameterJinja.Template
		for _, v := range parametersAndValues {
			if v.Value.Type == orc.AppParameterValueTypeWorkflow {
				jobTpl := v.Value.Specification.Job
				if jobTpl.Present {
					if len(tpl) > 0 {
						tpl += "\n"
					}
					tpl += jobTpl.Value
				}
			}
		}

		sbatchTplSession := &sbatchTemplateSession{
			Applications:        ServiceConfig.Compute.Applications,
			VersionPolicy:       "loose",
			VersionTarget:       util.Option[string]{},
			Error:               nil,
			PreviouslyLoaded:    make(map[orc.NativeApplication]appCfgAndVersion),
			SrunOverride:        ServiceConfig.Compute.Srun,
			ParametersAndValues: parametersAndValues,
			Parameters:          job.Specification.Parameters,
		}

		load := tool.Description.LoadInstructions.Get()
		if load.Type == orc.ToolLoadInstructionsNative {
			sbatchTplSession.RequiredApplications = load.Applications
		}

		tpl = controller.PreprocessJinjaTemplate(tpl, sbatchTplSession, sbatchTemplate)

		srunConfig := sbatchTplSession.SrunOverride.GetOrDefault(
			ServiceConfig.Compute.Srun.GetOrDefault(cfg.SrunConfiguration{
				Command: "srun",
				Flags:   nil,
			}),
		)

		srunCommand := srunConfig.Command
		for _, flag := range srunConfig.Flags {
			srunCommand += " "
			srunCommand += orc.EscapeBash(flag)
		}
		srunCommand += " "

		jinjaContextParameters["srun"] = func() *exec.Value {
			return exec.AsSafeValue(srunCommand)
		}

		jinjaContext = exec.NewContext(jinjaContextParameters)

		{
			safeMap := util.SanitizeMapForSerialization(jinjaContextParameters)
			buf := &bytes.Buffer{}
			yamlEncoder := yaml.NewEncoder(buf)
			_ = yamlEncoder.Encode(safeMap)

			jinjaTemplateFile = tpl
			jinjaParametersFile = string(buf.Bytes())
		}

		output, err := controller.ExecuteJinjaTemplate(tpl, 0, nil, jinjaContext, controller.JinjaFlagsNoPreProcess)
		if err != nil {
			log.Warn("Jinja generation failure for %s %s",
				job.Specification.Application.Name, job.Specification.Application.Version)
			cli = "# Failure during generation of invocation: " + err.Error()
		} else {
			cli = output
		}
	} else {
		jinjaContext = exec.NewContext(jinjaContextParameters)
		var cliInvocation []string

		for _, invParam := range invocation {
			newCliArgs := controller.BuildParameter(invParam, parametersAndValues, false, argBuilder, jinjaContext)
			for _, cliArg := range newCliArgs {
				cliInvocation = append(cliInvocation, orc.EscapeBash(cliArg))
			}
		}

		cli = strings.Join(cliInvocation, " ")
	}

	boundEnvironment := map[string]string{}
	{
		for key, param := range application.Environment {
			args := controller.BuildParameter(param, parametersAndValues, true, argBuilder, jinjaContext)
			value := orc.EscapeBash(strings.Join(args, " "))
			boundEnvironment[key] = value
		}

		ucloudCtx := jinjaContextParameters["ucloud"].(map[string]any)
		bindUCloudEnvironmentVariables("UCLOUD", boundEnvironment, ucloudCtx)
	}

	var modulesToLoad []string
	for _, module := range tool.Description.RequiredModules {
		modulesToLoad = append(modulesToLoad, module)
	}

	builder := &strings.Builder{}
	{
		appendLine(builder, "#!/usr/bin/env -S bash --login")
		for k, v := range directives {
			appendLine(builder, "#SBATCH --%v %v", k, v)
		}
		appendLine(builder, "")

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

		// Run the actual program
		// ---------------------------------------------------------
		appendLine(builder, "%s", cli)
	}

	return SBatchResult{
		Content:             builder.String(),
		DynamicTargets:      *targets,
		JinjaTemplateFile:   jinjaTemplateFile,
		JinjaParametersFile: jinjaParametersFile,
	}
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

var directivesWhichCannotBeChanged = []string{
	"account",
	"partition",
	"parsable",
	"comment",
}

const ucloudSlurmComment = "UCloud job"
