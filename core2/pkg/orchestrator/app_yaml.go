package orchestrator

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"gopkg.in/yaml.v3"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type A2Yaml struct {
	Name            string                  `yaml:"name"`
	Version         string                  `yaml:"version"`
	Software        A2Software              `yaml:"software"`
	Title           util.Option[string]     `yaml:"title"`
	Description     util.Option[string]     `yaml:"description"`
	License         util.Option[string]     `yaml:"license"`
	Documentation   util.Option[string]     `yaml:"documentation"`
	Features        util.Option[A2Features] `yaml:"features"`
	Modules         util.Option[A2Module]   `yaml:"modules"`
	Parameters      map[string]A2Parameter  `yaml:"parameters"`
	ParametersOrder []string                `yaml:"-"` // needed to preserve YAML declaration order
	Sbatch          map[string]string       `yaml:"sbatch"`
	Invocation      string                  `yaml:"invocation"`
	Environment     map[string]string       `yaml:"environment"`
	Web             util.Option[A2Web]      `yaml:"web"`
	Vnc             util.Option[A2Vnc]      `yaml:"vnc"`
	Ssh             util.Option[A2Ssh]      `yaml:"ssh"`
	Extensions      []string                `yaml:"extensions"`
}

type A2SoftwareKind string

const (
	A2SoftwareNative         A2SoftwareKind = "Native"
	A2SoftwareContainer      A2SoftwareKind = "Container"
	A2SoftwareVirtualMachine A2SoftwareKind = "VirtualMachine"
)

var A2SoftwareKinds = []A2SoftwareKind{
	A2SoftwareNative,
	A2SoftwareContainer,
	A2SoftwareVirtualMachine,
}

type A2Software struct {
	Type           A2SoftwareKind            `yaml:"type"`
	Native         *A2NativeSoftware         `yaml:"-"`
	Container      *A2ContainerSoftware      `yaml:"-"`
	VirtualMachine *A2VirtualMachineSoftware `yaml:"-"`
}

type A2NativeSoftware struct {
	Load []A2ApplicationToLoad `yaml:"load"`
}

type A2ApplicationToLoad struct {
	Name    string `yaml:"name"`
	Version string `yaml:"version"`
}

type A2ContainerSoftware struct {
	Image string `yaml:"image"`
}

type A2VirtualMachineSoftware struct {
	Image string `yaml:"image"`
}

func (s *A2Software) UnmarshalYAML(n *yaml.Node) error {
	var t struct {
		Type string `yaml:"type"`
	}
	if err := n.Decode(&t); err != nil {
		return err
	}
	switch t.Type {
	case "Native":
		var v A2NativeSoftware
		if err := n.Decode(&v); err != nil {
			return err
		}
		s.Type = "Native"
		s.Native = &v
		return nil
	case "Container":
		var v A2ContainerSoftware
		if err := n.Decode(&v); err != nil {
			return err
		}
		s.Type = "Container"
		s.Container = &v
		return nil
	case "VirtualMachine":
		var v A2VirtualMachineSoftware
		if err := n.Decode(&v); err != nil {
			return err
		}
		s.Type = "VirtualMachine"
		s.VirtualMachine = &v
		return nil
	default:
		return fmt.Errorf("unknown software type: %q", t.Type)
	}
}

type A2Parameter struct {
	Type          string            `yaml:"type"`
	File          *A2ParamFile      `yaml:"-"`
	Directory     *A2ParamDirectory `yaml:"-"`
	License       *A2ParamLicense   `yaml:"-"`
	Job           *A2ParamJob       `yaml:"-"`
	PublicIP      *A2ParamPublicIp  `yaml:"-"`
	Integer       *A2ParamInt       `yaml:"-"`
	FloatingPoint *A2ParamFloat     `yaml:"-"`
	Boolean       *A2ParamBool      `yaml:"-"`
	Text          *A2ParamText      `yaml:"-"`
	TextArea      *A2ParamTextArea  `yaml:"-"`
	Enumeration   *A2ParamEnum      `yaml:"-"`
	Workflow      *A2ParamWorkflow  `yaml:"-"`
}

type A2ParamBase struct {
	Title       string `yaml:"title"`
	Description string `yaml:"description"`
	Optional    bool   `yaml:"optional"`
}

type A2ParamFile struct {
	A2ParamBase `yaml:",inline"`
}

type A2ParamDirectory struct {
	A2ParamBase `yaml:",inline"`
}

type A2ParamLicense struct {
	A2ParamBase `yaml:",inline"`
}

type A2ParamJob struct {
	A2ParamBase `yaml:",inline"`
}

type A2ParamPublicIp struct {
	A2ParamBase `yaml:",inline"`
}

type A2ParamInt struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[int64] `yaml:"defaultValue"`
	Min          util.Option[int64] `yaml:"min"`
	Max          util.Option[int64] `yaml:"max"`
	Step         util.Option[int64] `yaml:"step"`
}

type A2ParamFloat struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[float64] `yaml:"defaultValue"`
	Min          util.Option[float64] `yaml:"min"`
	Max          util.Option[float64] `yaml:"max"`
	Step         util.Option[float64] `yaml:"step"`
}

type A2ParamBool struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[bool] `yaml:"defaultValue"`
}

type A2ParamText struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[string] `yaml:"defaultValue"`
}

type A2ParamTextArea struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[string] `yaml:"defaultValue"`
}

type A2EnumOption struct {
	Title string `yaml:"title"`
	Value string `yaml:"value"`
}

type A2ParamEnum struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[string] `yaml:"defaultValue"` // references the value
	Options      []A2EnumOption      `yaml:"options"`
}

type A2ParamWorkflow struct {
	A2ParamBase `yaml:",inline"`
	Init        util.Option[string]    `yaml:"init"`
	Job         util.Option[string]    `yaml:"job"`
	Readme      util.Option[string]    `yaml:"readme"`
	Parameters  map[string]A2Parameter `yaml:"parameters"`
}

func (p *A2ParamWorkflow) UnmarshalYAML(n *yaml.Node) error {
	type alias struct {
		A2ParamBase `yaml:",inline"`
		Init        util.Option[string]    `yaml:"init"`
		Job         util.Option[string]    `yaml:"job"`
		Readme      util.Option[string]    `yaml:"readme"`
		Parameters  map[string]A2Parameter `yaml:"parameters"`
	}
	var a alias
	if err := n.Decode(&a); err != nil {
		return err
	}
	if a.Parameters == nil {
		a.Parameters = map[string]A2Parameter{}
	}
	*p = A2ParamWorkflow(a)
	return nil
}

func (p *A2Parameter) UnmarshalYAML(n *yaml.Node) error {
	var t struct {
		Type string `yaml:"type"`
	}
	if err := n.Decode(&t); err != nil {
		return err
	}
	switch t.Type {
	case "File":
		var v A2ParamFile
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "File"
		p.File = &v
	case "Directory":
		var v A2ParamDirectory
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Directory"
		p.Directory = &v
	case "License":
		var v A2ParamLicense
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "License"
		p.License = &v
	case "Job":
		var v A2ParamJob
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Job"
		p.Job = &v
	case "PublicIP":
		var v A2ParamPublicIp
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "PublicIP"
		p.PublicIP = &v
	case "Integer":
		var v A2ParamInt
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Integer"
		p.Integer = &v
	case "FloatingPoint":
		var v A2ParamFloat
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "FloatingPoint"
		p.FloatingPoint = &v
	case "Boolean":
		var v A2ParamBool
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Boolean"
		p.Boolean = &v
	case "Text":
		var v A2ParamText
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Text"
		p.Text = &v
	case "TextArea":
		var v A2ParamTextArea
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "TextArea"
		p.TextArea = &v
	case "Enumeration":
		var v A2ParamEnum
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Enumeration"
		p.Enumeration = &v
	case "Workflow":
		var v A2ParamWorkflow
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "Workflow"
		p.Workflow = &v
	default:
		return fmt.Errorf("unknown parameter type: %q", t.Type)
	}
	return nil
}

type A2Features struct {
	MultiNode   bool              `yaml:"multiNode"`
	Links       util.Option[bool] `yaml:"links"`
	IPAddresses util.Option[bool] `yaml:"ipAddresses"`
	Folders     util.Option[bool] `yaml:"folders"`
	JobLinking  util.Option[bool] `yaml:"jobLinking"`
}

type A2Web struct {
	Enabled bool             `yaml:"enabled"`
	Port    util.Option[int] `yaml:"port"`
}

type A2Vnc struct {
	Enabled  bool                `yaml:"enabled"`
	Port     util.Option[int]    `yaml:"port"`
	Password util.Option[string] `yaml:"password"`
}

type A2Ssh struct {
	Mode A2SshMode `yaml:"mode"`
}

type A2SshMode string

const (
	A2SshModeMandatory A2SshMode = "Mandatory"
	A2SshModeOptional  A2SshMode = "Optional"
	A2SshModeDisabled  A2SshMode = "Disabled"
)

var A2SshModeOptions = []A2SshMode{
	A2SshModeMandatory,
	A2SshModeOptional,
	A2SshModeDisabled,
}

type A2Module struct {
	MountPath string   `yaml:"mountPath"`
	Optional  []string `yaml:"optional"`
}

func (y *A2Yaml) UnmarshalYAML(n *yaml.Node) error {
	type alias A2Yaml
	var a alias

	// Decode normally first
	if err := n.Decode(&a); err != nil {
		return err
	}

	// Extract parameters order from the raw node
	if a.Parameters == nil {
		a.Parameters = map[string]A2Parameter{}
	}

	a.ParametersOrder = nil
	if n.Kind == yaml.MappingNode {
		for i := 0; i < len(n.Content); i += 2 {
			k := n.Content[i]
			v := n.Content[i+1]
			if k.Value != "parameters" {
				continue
			}
			if v.Kind != yaml.MappingNode {
				// parameters present but not a mapping
				break
			}

			for j := 0; j < len(v.Content); j += 2 {
				keyNode := v.Content[j]
				a.ParametersOrder = append(a.ParametersOrder, keyNode.Value)
			}
			break
		}
	}

	*y = A2Yaml(a)
	return nil
}

func (y *A2Yaml) Normalize() (orcapi.Application, *util.HttpError) {
	var err *util.HttpError
	var mappedParameters []orcapi.ApplicationParameter
	mappedEnvironment := map[string]orcapi.InvocationParameter{}
	mappedSbatch := map[string]orcapi.InvocationParameter{}
	mappedAppType := orcapi.ApplicationTypeBatch
	mappedModules := util.OptNone[orcapi.ModulesSection]()

	util.ValidateString(&y.Name, "name", 0, &err)
	util.ValidateString(&y.Version, "version", 0, &err)
	if y.Title.Present {
		util.ValidateString(&y.Title.Value, "title", 0, &err)
	}
	if y.Description.Present {
		util.ValidateString(&y.Description.Value, "description",
			util.StringValidationAllowMultiline|util.StringValidationAllowLong, &err)
	}
	if y.Documentation.Present {
		util.ValidateString(&y.Documentation.Value, "documentation", 0, &err)
	}
	for i := 0; i < len(y.Extensions); i++ {
		util.ValidateString(&y.Extensions[i], fmt.Sprintf("extensions[%d]", i), 0, &err)
	}

	mappedTool := orcapi.ToolDescription{
		Info: orcapi.NameAndVersion{
			Name:    y.Name,
			Version: y.Version,
		},
		DefaultNumberOfNodes:  1,
		DefaultTimeAllocation: orcapi.SimpleDuration{Hours: 1},
		RequiredModules:       nil,
		Authors:               []string{"UCloud"},
		Title:                 y.Title.GetOrDefault(y.Name),
		Description:           y.Description.GetOrDefault(""),
	}
	util.ValidateEnum(&y.Software.Type, A2SoftwareKinds, "software.type", &err)
	switch y.Software.Type {
	case A2SoftwareContainer:
		mappedTool.Backend = orcapi.ToolBackendDocker

		if y.Software.Container == nil {
			err = util.MergeHttpErr(err, util.HttpErr(
				http.StatusBadRequest,
				"missing container information in 'software'",
			))
		} else {
			util.ValidateString(&y.Software.Container.Image, "software.image", 0, &err)

			mappedTool.Container = y.Software.Container.Image
			mappedTool.Image = y.Software.Container.Image
		}

	case A2SoftwareVirtualMachine:
		mappedTool.Backend = orcapi.ToolBackendVirtualMachine

		if y.Software.VirtualMachine == nil {
			err = util.MergeHttpErr(err, util.HttpErr(
				http.StatusBadRequest,
				"missing virtual machine information in 'software'",
			))
		} else {
			util.ValidateString(&y.Software.VirtualMachine.Image, "software.image", 0, &err)

			mappedTool.Container = y.Software.VirtualMachine.Image
			mappedTool.Image = y.Software.VirtualMachine.Image
		}

	case A2SoftwareNative:
		mappedTool.Backend = orcapi.ToolBackendNative

		if y.Software.Native == nil {
			err = util.MergeHttpErr(err, util.HttpErr(
				http.StatusBadRequest,
				"missing native information in 'software'",
			))
		} else {
			instr := orcapi.ToolLoadInstructions{
				Type: orcapi.ToolLoadInstructionsNative,
			}

			modulesToLoad := y.Software.Native.Load
			for i := 0; i < len(modulesToLoad); i++ {
				mod := &modulesToLoad[i]
				util.ValidateString(&mod.Name, fmt.Sprintf("software.load[%d].name", i), 0, &err)
				util.ValidateString(&mod.Version, fmt.Sprintf("software.load[%d].version", i), 0, &err)

				instr.Applications = append(instr.Applications, orcapi.NativeApplication{
					Name:    mod.Name,
					Version: mod.Version,
				})
			}

			mappedTool.LoadInstructions.Set(instr)
		}
	}

	for _, paramName := range y.ParametersOrder {
		param := y.Parameters[paramName]
		mapped := orcapi.ApplicationParameter{
			Name: paramName,
		}

		util.ValidateString(&paramName, fmt.Sprintf("parameters.%s", paramName), 0, &err)

		var base A2ParamBase

		if param.File != nil {
			base = param.File.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeInputFile
		} else if param.Directory != nil {
			base = param.Directory.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeInputDirectory
		} else if param.License != nil {
			base = param.License.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeLicenseServer
		} else if param.Job != nil {
			base = param.Job.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypePeer
		} else if param.PublicIP != nil {
			base = param.PublicIP.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeNetworkIp
		} else if param.Integer != nil {
			base = param.Integer.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeInteger

			i := param.Integer

			if i.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(i.DefaultValue.Value)
			}

			if i.DefaultValue.Present && i.Min.Present && i.DefaultValue.Value < i.Min.Value {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s default value must not be lower than the minimum value",
					paramName,
				))
			}

			if i.Min.Present && i.Max.Present {
				if i.Min.Value > i.Max.Value {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s min value must not exceed max value (%v > %v)",
						paramName, i.Min.Value, i.Max.Value,
					))
				}
			}

			if i.Step.Present && i.Step.Value <= 0 {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s step value must be > 0",
					paramName,
				))
			}
		} else if param.FloatingPoint != nil {
			base = param.FloatingPoint.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeFloatingPoint

			f := param.FloatingPoint

			if f.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(f.DefaultValue.Value)
			}

			if f.DefaultValue.Present && f.Min.Present && f.DefaultValue.Value < f.Min.Value {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s default value must not be lower than the minimum value",
					paramName,
				))
			}

			if f.Min.Present && f.Max.Present {
				if f.Min.Value > f.Max.Value {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s min value must not exceed max value (%v > %v)",
						paramName, f.Min.Value, f.Max.Value,
					))
				}
			}

			if f.Step.Present && f.Step.Value <= 0 {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s step value must be > 0",
					paramName,
				))
			}
		} else if param.Boolean != nil {
			base = param.Boolean.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeBoolean
			mapped.TrueValue = "true"
			mapped.FalseValue = "false"

			if param.Boolean.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(param.Boolean.DefaultValue.Value)
			}
		} else if param.Text != nil {
			base = param.Text.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeText

			if param.Text.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(param.Text.DefaultValue.Value)
			}
		} else if param.TextArea != nil {
			base = param.TextArea.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeTextArea

			if param.TextArea.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(param.TextArea.DefaultValue.Value)
			}
		} else if param.Enumeration != nil {
			base = param.Enumeration.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeEnumeration

			e := param.Enumeration

			if e.DefaultValue.Present {
				defaultValue := e.DefaultValue.Value
				jsDefaultValue, _ := json.Marshal(defaultValue)
				mapped.DefaultValue = jsDefaultValue

				found := false
				for _, opt := range e.Options {
					if opt.Value == defaultValue {
						found = true
						break
					}
				}

				if !found {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s.defaultValue must be present in options",
						paramName,
					))
				}
			}

			for i, opt := range e.Options {
				util.ValidateString(&opt.Title, fmt.Sprintf("%s.options[%v].title", paramName, i), 0, &err)
				util.ValidateString(&opt.Value, fmt.Sprintf("%s.options[%v].value", paramName, i), 0, &err)

				mapped.Options = append(mapped.Options, orcapi.EnumOption{
					Name:  opt.Title,
					Value: opt.Value,
				})
			}
		} else if param.Workflow != nil {
			base = param.Workflow.A2ParamBase
			mapped.Type = orcapi.ApplicationParameterTypeWorkflow
		}

		util.ValidateString(&base.Title, paramName, 0, &err)
		util.ValidateString(&base.Description, fmt.Sprintf("%s.description", paramName),
			util.StringValidationAllowMultiline|util.StringValidationAllowEmpty, &err)

		mapped.Title = base.Title
		mapped.Description = base.Description
		mapped.Optional = base.Optional

		mappedParameters = append(mappedParameters, mapped)
	}

	for name, value := range y.Environment {
		util.ValidateString(&name, fmt.Sprintf("environment.%s (key)", name), 0, &err)
		util.ValidateString(&value, fmt.Sprintf("environment.%s (value)", name), 0, &err)

		mappedEnvironment[name] = orcapi.InvocationParameter{
			Type:                    orcapi.InvocationParameterTypeWord,
			InvocationParameterWord: orcapi.InvocationParameterWord{Word: value},
		}
	}

	for name, value := range y.Sbatch {
		util.ValidateString(&name, fmt.Sprintf("sbatch.%s (key)", name), 0, &err)
		util.ValidateString(&value, fmt.Sprintf("sbatch.%s (value)", name), 0, &err)

		mappedSbatch[name] = orcapi.InvocationParameter{
			Type:                    orcapi.InvocationParameterTypeWord,
			InvocationParameterWord: orcapi.InvocationParameterWord{Word: value},
		}
	}

	if y.Modules.Present {
		mods := y.Modules.Value
		mappedModules.Set(orcapi.ModulesSection{
			MountPath: mods.MountPath,
			Optional:  mods.Optional,
		})
	}

	if y.Vnc.Present {
		if y.Vnc.Value.Enabled {
			mappedAppType = orcapi.ApplicationTypeVnc
			p := y.Vnc.Value.Port
			if p.Present {
				if p.Value <= 0 {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"vnc.port must not be <= 0",
					))
				} else if p.Value >= 1024*64 {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"vnc.port must be < 65536",
					))
				}
			}
		} else {
			y.Vnc.Clear()
		}
	}

	if y.Web.Present {
		mappedAppType = orcapi.ApplicationTypeWeb

		p := y.Web.Value.Port
		if p.Present {
			if p.Value <= 0 {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"web.port must not be <= 0",
				))
			} else if p.Value >= 1024*64 {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"web.port must be < 65536",
				))
			}
		}

		if !y.Web.Value.Enabled {
			y.Web.Clear()
		}
	}

	if y.Ssh.Present {
		util.ValidateEnum(&y.Ssh.Value.Mode, A2SshModeOptions, "ssh.mode", &err)
	}

	if err != nil {
		return orcapi.Application{}, err
	} else {
		return orcapi.Application{
			WithAppMetadata: orcapi.WithAppMetadata{
				Metadata: orcapi.ApplicationMetadata{
					NameAndVersion: orcapi.NameAndVersion{
						Name:    y.Name,
						Version: y.Version,
					},
					Authors:     []string{"UCloud"},
					Title:       y.Title.GetOrDefault(y.Name),
					Description: y.Description.GetOrDefault(""),
					Website:     y.Documentation.GetOrDefault(""),
					Public:      false,
					CreatedAt:   fndapi.Timestamp(time.Now()),
				},
			},
			WithAppInvocation: orcapi.WithAppInvocation{
				Invocation: orcapi.ApplicationInvocationDescription{
					OutputFileGlobs: []string{"*"},
					ApplicationType: mappedAppType,

					Tool: orcapi.ToolReference{
						NameAndVersion: orcapi.NameAndVersion{
							Name:    y.Name,
							Version: y.Version,
						},
						Tool: util.OptValue[orcapi.Tool](orcapi.Tool{
							Owner:       "UCloud",
							CreatedAt:   fndapi.Timestamp(time.Now()),
							ModifiedAt:  fndapi.Timestamp(time.Now()),
							Description: mappedTool,
						}),
					},

					Parameters: util.NonNilSlice(mappedParameters),

					Invocation: []orcapi.InvocationParameter{
						{
							Type: orcapi.InvocationParameterTypeJinja,
							InvocationParameterJinja: orcapi.InvocationParameterJinja{
								Template: y.Invocation,
							},
						},
					},

					Vnc: util.OptMap(y.Vnc, func(value A2Vnc) orcapi.VncDescription {
						return orcapi.VncDescription{
							Password: value.Password.GetOrDefault(""),
							Port:     uint16(value.Port.Value),
						}
					}),

					Web: util.OptMap(y.Web, func(value A2Web) orcapi.WebDescription {
						return orcapi.WebDescription{Port: uint16(value.Port.GetOrDefault(80))}
					}),

					Ssh: util.OptMap(y.Ssh, func(value A2Ssh) orcapi.SshDescription {
						res := orcapi.SshDescription{}
						switch value.Mode {
						case A2SshModeDisabled:
							res.Mode = orcapi.SshModeDisabled
						case A2SshModeOptional:
							res.Mode = orcapi.SshModeOptional
						case A2SshModeMandatory:
							res.Mode = orcapi.SshModeMandatory
						}
						return res
					}),

					Container: orcapi.ContainerDescription{
						ChangeWorkingDirectory: true,
						RunAsRoot:              true,
						RunAsRealUser:          false,
					},

					AllowAdditionalPeers:  y.Features.Value.JobLinking,
					AllowAdditionalMounts: y.Features.Value.Folders,
					AllowMultiNode:        util.OptValue(y.Features.Value.MultiNode),
					AllowPublicIp:         y.Features.Value.IPAddresses,
					AllowPublicLink:       y.Features.Value.Links,

					Environment:    mappedEnvironment,
					Modules:        mappedModules,
					Sbatch:         mappedSbatch,
					FileExtensions: y.Extensions,
				},
			},
		}, nil
	}
}

func marshalJson(value any) []byte {
	b, _ := json.Marshal(value)
	return b
}
