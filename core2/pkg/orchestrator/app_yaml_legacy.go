package orchestrator

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"gopkg.in/yaml.v3"
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orc2"
	"ucloud.dk/shared/pkg/util"
)

// Models
// =====================================================================================================================

type A1ApplicationParameterYaml struct {
	Type           string            `yaml:"type"`
	InputFile      *A1InputFile      `yaml:"-"`
	InputDirectory *A1InputDirectory `yaml:"-"`
	Text           *A1Text           `yaml:"-"`
	TextArea       *A1TextArea       `yaml:"-"`
	Integer        *A1Integer        `yaml:"-"`
	FloatingPoint  *A1FloatingPoint  `yaml:"-"`
	Bool           *A1Bool           `yaml:"-"`
	Enumeration    *A1Enumeration    `yaml:"-"`
	Peer           *A1Peer           `yaml:"-"`
	LicenseServer  *A1LicenseServer  `yaml:"-"`
	Ingress        *A1Ingress        `yaml:"-"`
	NetworkIP      *A1NetworkIP      `yaml:"-"`
}

type A1ParamBase struct {
	Name         string                 `yaml:"name"`
	Optional     bool                   `yaml:"optional"`
	Title        util.Option[string]    `yaml:"title"`
	Description  string                 `yaml:"description"`
	DefaultValue util.Option[yaml.Node] `yaml:"defaultValue"`
}

type A1InputFile struct {
	A1ParamBase `yaml:",inline"`
	Type        string `yaml:"type"`
}

type A1InputDirectory struct {
	A1ParamBase `yaml:",inline"`
	Type        string `yaml:"type"`
}

type A1Text struct {
	A1ParamBase `yaml:",inline"`
	Type        string `yaml:"type"`
}

type A1TextArea struct {
	A1ParamBase `yaml:",inline"`
	Type        string `yaml:"type"`
}

type A1Integer struct {
	A1ParamBase `yaml:",inline"`
	Type        string              `yaml:"type"`
	Min         util.Option[int64]  `yaml:"min"`
	Max         util.Option[int64]  `yaml:"max"`
	Step        util.Option[int64]  `yaml:"step"`
	UnitName    util.Option[string] `yaml:"unitName"`
}

type A1FloatingPoint struct {
	A1ParamBase `yaml:",inline"`
	Type        string               `yaml:"type"`
	Min         util.Option[float64] `yaml:"min"`
	Max         util.Option[float64] `yaml:"max"`
	Step        util.Option[float64] `yaml:"step"`
	UnitName    util.Option[string]  `yaml:"unitName"`
}

type A1Bool struct {
	A1ParamBase `yaml:",inline"`
	Type        string `yaml:"type"`
	TrueValue   string `yaml:"trueValue"`
	FalseValue  string `yaml:"falseValue"`
}

type A1EnumOption struct {
	Name  string `yaml:"name"`
	Value string `yaml:"value"`
}

type A1Enumeration struct {
	A1ParamBase `yaml:",inline"`
	Type        string         `yaml:"type"`
	Options     []A1EnumOption `yaml:"options"`
}

type A1Peer struct {
	A1ParamBase          `yaml:",inline"`
	SuggestedApplication util.Option[string] `yaml:"suggestedApplication"`
}

type A1Ingress struct {
	A1ParamBase `yaml:",inline"`
}

type A1LicenseServer struct {
	A1ParamBase `yaml:",inline"`
	Tagged      []string `yaml:"tagged"`
}

type A1NetworkIP struct {
	A1ParamBase `yaml:",inline"`
}

func (p *A1ApplicationParameterYaml) UnmarshalYAML(n *yaml.Node) error {
	var t struct {
		Type string `yaml:"type"`
	}
	if err := n.Decode(&t); err != nil {
		return err
	}
	switch t.Type {
	case "input_file":
		var v A1InputFile
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "input_file"
		p.Type = v.Type
		p.InputFile = &v
	case "input_directory":
		var v A1InputDirectory
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "input_directory"
		p.Type = v.Type
		p.InputDirectory = &v
	case "text":
		var v A1Text
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "text"
		p.Type = v.Type
		p.Text = &v
	case "textarea":
		var v A1TextArea
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "textarea"
		p.Type = v.Type
		p.TextArea = &v
	case "integer":
		var v A1Integer
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "integer"
		p.Type = v.Type
		p.Integer = &v
	case "floating_point":
		var v A1FloatingPoint
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "floating_point"
		p.Type = v.Type
		p.FloatingPoint = &v
	case "boolean":
		var v A1Bool
		if err := n.Decode(&v); err != nil {
			return err
		}
		if v.TrueValue == "" {
			v.TrueValue = "true"
		}
		if v.FalseValue == "" {
			v.FalseValue = "false"
		}
		v.Type = "boolean"
		p.Type = v.Type
		p.Bool = &v
	case "enumeration":
		var v A1Enumeration
		if err := n.Decode(&v); err != nil {
			return err
		}
		if v.Options == nil {
			v.Options = []A1EnumOption{}
		}
		v.Type = "enumeration"
		p.Type = v.Type
		p.Enumeration = &v
	case "peer":
		var v A1Peer
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "peer"
		p.Peer = &v
	case "license_server":
		var v A1LicenseServer
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "license_server"
		p.LicenseServer = &v
	case "ingress":
		var v A1Ingress
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "ingress"
		p.Ingress = &v
	case "network_ip":
		var v A1NetworkIP
		if err := n.Decode(&v); err != nil {
			return err
		}
		p.Type = "network_ip"
		p.NetworkIP = &v
	default:
		return fmt.Errorf("unknown parameter type: %q", t.Type)
	}
	return nil
}

type A1Yaml struct {
	Name                  string                                   `yaml:"name"`
	Version               string                                   `yaml:"version"`
	Tool                  orcapi.NameAndVersion                    `yaml:"tool"`
	Authors               []string                                 `yaml:"authors"`
	Title                 string                                   `yaml:"title"`
	Description           string                                   `yaml:"description"`
	Invocation            []A1InvocationParameter                  `yaml:"invocation"`
	Parameters            map[string]A1ApplicationParameterYaml    `yaml:"parameters"`
	OutputFileGlobs       []string                                 `yaml:"outputFileGlobs"`
	ApplicationType       util.Option[orcapi.ApplicationType]      `yaml:"applicationType"`
	Vnc                   util.Option[orcapi.VncDescription]       `yaml:"vnc"`
	Web                   util.Option[orcapi.WebDescription]       `yaml:"web"`
	Ssh                   util.Option[orcapi.SshDescription]       `yaml:"ssh"`
	Container             util.Option[orcapi.ContainerDescription] `yaml:"container"`
	Environment           map[string]A1InvocationParameter         `yaml:"environment"`
	AllowAdditionalMounts util.Option[bool]                        `yaml:"allowAdditionalMounts"`
	AllowAdditionalPeers  util.Option[bool]                        `yaml:"allowAdditionalPeers"`
	AllowMultiNode        util.Option[bool]                        `yaml:"allowMultiNode"`
	AllowPublicIp         util.Option[bool]                        `yaml:"allowPublicIp"`
	AllowPublicLink       util.Option[bool]                        `yaml:"allowPublicLink"`
	FileExtensions        []string                                 `yaml:"fileExtensions"`
	LicenseServers        []string                                 `yaml:"licenseServers"`
	Website               util.Option[string]                      `yaml:"website"`
	Modules               util.Option[A1Module]                    `yaml:"modules"`
}

func (y *A1Yaml) UnmarshalYAML(n *yaml.Node) error {
	type alias A1Yaml
	var a alias
	if err := n.Decode(&a); err != nil {
		return err
	}
	if a.Parameters == nil {
		a.Parameters = map[string]A1ApplicationParameterYaml{}
	}
	if a.OutputFileGlobs == nil {
		a.OutputFileGlobs = []string{}
	}
	if a.FileExtensions == nil {
		a.FileExtensions = []string{}
	}
	if a.LicenseServers == nil {
		a.LicenseServers = []string{}
	}
	*y = A1Yaml(a)
	return nil
}

type A1StringList []string

func (s *A1StringList) UnmarshalYAML(value *yaml.Node) error {
	switch value.Kind {
	case yaml.ScalarNode:
		var str string
		if err := value.Decode(&str); err != nil {
			return err
		}
		*s = []string{str}
		return nil
	case yaml.SequenceNode:
		var list []string
		if err := value.Decode(&list); err != nil {
			return err
		}
		*s = list
		return nil
	default:
		return fmt.Errorf("unexpected YAML node kind %d", value.Kind)
	}
}

type A1InvocationParameter struct {
	Type                        orcapi.InvocationParameterType `json:"type" yaml:"type"`
	InvocationParameterEnv      `yaml:",inline"`
	InvocationParameterWord     `yaml:",inline"`
	InvocationParameterVar      `yaml:",inline"`
	InvocationParameterBoolFlag `yaml:",inline"`
	InvocationParameterJinja    `yaml:",inline"`
}

type InvocationParameterEnv struct {
	Variable string `json:"variable,omitempty" yaml:"variable"`
}

type InvocationParameterWord struct {
	Word string `json:"word,omitempty" yaml:"word"`
}

type InvocationParameterVar struct {
	VariableNames             A1StringList `json:"variableNames,omitempty" yaml:"vars"`
	PrefixGlobal              string       `json:"prefixGlobal,omitempty" yaml:"prefixGlobal"`
	SuffixGlobal              string       `json:"suffixGlobal,omitempty" yaml:"suffixGlobal"`
	PrefixVariable            string       `json:"prefixVariable,omitempty" yaml:"prefixVariable"`
	SuffixVariable            string       `json:"suffixVariable,omitempty" yaml:"suffixVariable"`
	IsPrefixVariablePartOfArg bool         `json:"isPrefixVariablePartOfArg,omitempty" yaml:"isPrefixVariablePartOfArg"`
	IsSuffixVariablePartOfArg bool         `json:"isSuffixVariablePartOfArg,omitempty" yaml:"isSuffixVariablePartOfArg"`
}

type InvocationParameterBoolFlag struct {
	VariableName string `json:"variableName" yaml:"var"`
	Flag         string `json:"flag,omitempty" yaml:"flag"`
}

type InvocationParameterJinja struct {
	Template string `json:"template,omitempty" yaml:"template"`
}

func (p *A1InvocationParameter) UnmarshalYAML(n *yaml.Node) error {
	if n.Tag == "!!str" || n.Tag == "!!int" {
		var word string
		_ = n.Decode(&word)
		p.Type = orcapi.InvocationParameterTypeWord
		p.Word = word
	} else {
		var typeWrapper struct {
			Type string
		}
		_ = n.Decode(&typeWrapper)

		p.Type = orcapi.InvocationParameterType(typeWrapper.Type)

		var err error

		switch orcapi.InvocationParameterType(typeWrapper.Type) {
		case orcapi.InvocationParameterTypeEnv:
			err = n.Decode(&p.InvocationParameterEnv)
		case orcapi.InvocationParameterTypeWord:
			err = n.Decode(&p.InvocationParameterWord)
		case orcapi.InvocationParameterTypeVar:
			err = n.Decode(&p.InvocationParameterVar)
		case orcapi.InvocationParameterTypeBoolFlag, "flag":
			err = n.Decode(&p.InvocationParameterBoolFlag)
		case orcapi.InvocationParameterTypeJinja:
			err = n.Decode(&p.InvocationParameterJinja)
		default:
			err = util.HttpErr(http.StatusBadRequest, "unknown invocation parameter type")
		}

		return err
	}
	return nil
}

func (p *A1InvocationParameter) ToParameter() orcapi.InvocationParameter {
	switch p.Type {
	case orcapi.InvocationParameterTypeEnv:
		return orcapi.InvocationParameter{
			Type: p.Type,
			InvocationParameterEnv: orcapi.InvocationParameterEnv{
				Variable: p.InvocationParameterEnv.Variable,
			},
		}
	case orcapi.InvocationParameterTypeWord:
		return orcapi.InvocationParameter{
			Type: p.Type,
			InvocationParameterWord: orcapi.InvocationParameterWord{
				Word: p.InvocationParameterWord.Word,
			},
		}
	case orcapi.InvocationParameterTypeVar:
		return orcapi.InvocationParameter{
			Type: p.Type,
			InvocationParameterVar: orcapi.InvocationParameterVar{
				VariableNames:             p.VariableNames,
				PrefixGlobal:              p.PrefixGlobal,
				SuffixGlobal:              p.SuffixGlobal,
				PrefixVariable:            p.PrefixVariable,
				SuffixVariable:            p.SuffixVariable,
				IsPrefixVariablePartOfArg: p.IsPrefixVariablePartOfArg,
				IsSuffixVariablePartOfArg: p.IsSuffixVariablePartOfArg,
			},
		}
	case orcapi.InvocationParameterTypeBoolFlag, "flag":
		return orcapi.InvocationParameter{
			Type: orcapi.InvocationParameterTypeBoolFlag,
			InvocationParameterBoolFlag: orcapi.InvocationParameterBoolFlag{
				VariableName: p.InvocationParameterBoolFlag.VariableName,
				Flag:         p.InvocationParameterBoolFlag.Flag,
			},
		}
	case orcapi.InvocationParameterTypeJinja:
		return orcapi.InvocationParameter{
			Type: p.Type,
			InvocationParameterJinja: orcapi.InvocationParameterJinja{
				Template: p.InvocationParameterJinja.Template,
			},
		}
	}
	return orcapi.InvocationParameter{}
}

type A1Module struct {
	MountPath string   `yaml:"mountPath"`
	Optional  []string `yaml:"optional"`
}

type A1Tool struct {
	Name                  string                             `yaml:"name"`
	Version               string                             `yaml:"version"`
	Title                 string                             `yaml:"title"`
	Container             util.Option[string]                `yaml:"container"`
	Backend               orcapi.ToolBackend                 `yaml:"backend"`
	Authors               []string                           `yaml:"authors"`
	DefaultNumberOfNodes  util.Option[int]                   `yaml:"defaultNumberOfNodes"`
	DefaultTimeAllocation util.Option[orcapi.SimpleDuration] `yaml:"defaultTimeAllocation"`
	RequiredModules       []string                           `yaml:"requiredModules"`
	Description           string                             `yaml:"description"`
	License               string                             `yaml:"license"`
	Image                 util.Option[string]                `yaml:"image"`
	SupportedProviders    []string                           `yaml:"supportedProviders"`
}

// Normalization
// =====================================================================================================================

func a1ReadDefaultValue[T any](n *yaml.Node, fieldName string, outErr **util.HttpError) (T, json.RawMessage) {
	type wrapper struct {
		Value T `yaml:"value"`
	}

	var w wrapper
	err := n.Decode(&w)
	if err == nil {
		data, _ := json.Marshal(w.Value)
		return w.Value, data
	} else {
		var t T
		var data []byte
		err = n.Decode(&t)
		if err != nil {
			*outErr = util.MergeHttpErr(*outErr, util.HttpErr(http.StatusBadRequest, "%s invalid default value", fieldName))
		} else {
			data, _ = json.Marshal(t)
		}
		return t, data
	}
}

func (y *A1Yaml) Normalize() (orcapi.Application, *util.HttpError) {
	var err *util.HttpError

	util.ValidateString(&y.Name, "name", 0, &err)
	util.ValidateString(&y.Version, "version", 0, &err)
	util.ValidateString(&y.Title, "title", 0, &err)
	util.ValidateString(&y.Description, "description",
		util.StringValidationAllowEmpty|util.StringValidationAllowMultiline, &err)

	if !y.ApplicationType.Present {
		y.ApplicationType.Set(orcapi.ApplicationTypeBatch)
	} else {
		util.ValidateEnum(&y.ApplicationType.Value, orcapi.ApplicationTypeOptions, "applicationType", &err)
	}

	appType := y.ApplicationType.GetOrDefault(orcapi.ApplicationTypeBatch)

	if y.Website.Present {
		util.ValidateString(&y.Website.Value, "website", 0, &err)
	}

	if appType != orcapi.ApplicationTypeWeb && y.Web.Present {
		err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest,
			"web can only be specified with app type = 'WEB'"))
	}

	if appType != orcapi.ApplicationTypeVnc && y.Vnc.Present {
		err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest,
			"vnc can only be specified with app type = 'VNC'"))
	}

	if y.Ssh.Present {
		ssh := y.Ssh.Value
		util.ValidateEnum(&ssh.Mode, orcapi.SshModeOptions, "ssh.mode", &err)
	}

	var mappedParameters []orcapi.ApplicationParameter
	for name, param := range y.Parameters {
		mapped := orcapi.ApplicationParameter{}

		fieldName := fmt.Sprintf("parameters[%v]", name)

		var base *A1ParamBase
		if param.InputFile != nil {
			mapped.Type = orcapi.ApplicationParameterTypeInputFile
			base = &param.InputFile.A1ParamBase
			if base.DefaultValue.Present {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s must not have a defaultValue",
					fieldName,
				))
			}
		} else if param.InputDirectory != nil {
			mapped.Type = orcapi.ApplicationParameterTypeInputDirectory
			base = &param.InputDirectory.A1ParamBase
			if base.DefaultValue.Present {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s must not have a defaultValue",
					fieldName,
				))
			}
		} else if param.Text != nil {
			mapped.Type = orcapi.ApplicationParameterTypeText
			base = &param.Text.A1ParamBase
			if base.DefaultValue.Present {
				_, jsDefault := a1ReadDefaultValue[string](&base.DefaultValue.Value, fieldName, &err)
				mapped.DefaultValue = jsDefault
			}
		} else if param.TextArea != nil {
			mapped.Type = orcapi.ApplicationParameterTypeTextArea
			base = &param.TextArea.A1ParamBase
			if base.DefaultValue.Present {
				_, jsDefault := a1ReadDefaultValue[string](&base.DefaultValue.Value, fieldName, &err)
				mapped.DefaultValue = jsDefault
			}
		} else if param.Integer != nil {
			mapped.Type = orcapi.ApplicationParameterTypeInteger
			i := param.Integer
			base = &i.A1ParamBase
			if base.DefaultValue.Present {
				_, jsDefault := a1ReadDefaultValue[int](&base.DefaultValue.Value, fieldName, &err)
				mapped.DefaultValue = jsDefault
			}

			if i.Min.Present && i.Max.Present {
				if i.Min.Value > i.Max.Value {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s min value must not exceed max value (%v > %v)",
						fieldName, i.Min.Value, i.Max.Value,
					))
				}
			}

			if i.Step.Present && i.Step.Value <= 0 {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s step value must be > 0",
					fieldName,
				))
			}

			if i.Min.Present {
				mapped.MinValue = i.Min.Value
			}

			if i.Max.Present {
				mapped.MaxValue = i.Max.Value
			}

			if i.Step.Present {
				mapped.Step = i.Step.Value
			}

			if i.UnitName.Present {
				util.ValidateString(&i.UnitName.Value, fmt.Sprintf("%s.unitName", fieldName), 0, &err)
				mapped.UnitName = i.UnitName.Value
			}
		} else if param.FloatingPoint != nil {
			mapped.Type = orcapi.ApplicationParameterTypeFloatingPoint
			f := param.FloatingPoint
			base = &param.FloatingPoint.A1ParamBase
			if base.DefaultValue.Present {
				defaultValue, jsDefault := a1ReadDefaultValue[float64](&base.DefaultValue.Value, fieldName, &err)
				mapped.DefaultValue = jsDefault

				if f.Min.Present && defaultValue < f.Min.Value {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s default value must not be lower than minimum",
						fieldName,
					))
				}

				if f.Max.Present && defaultValue > f.Max.Value {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s default value must not be higher than maximum",
						fieldName,
					))
				}
			}

			if f.Min.Present && f.Max.Present {
				if f.Min.Value > f.Max.Value {
					err = util.MergeHttpErr(err, util.HttpErr(
						http.StatusBadRequest,
						"%s min value must not exceed max value (%v > %v)",
						fieldName, f.Min.Value, f.Max.Value,
					))
				}
			}

			if f.Step.Present && f.Step.Value <= 0 {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s step value must be > 0",
					fieldName,
				))
			}

			if f.Min.Present {
				mapped.MinValue = f.Min.Value
			}

			if f.Max.Present {
				mapped.MaxValue = f.Max.Value
			}

			if f.Step.Present {
				mapped.Step = f.Step.Value
			}

			if f.UnitName.Present {
				util.ValidateString(&f.UnitName.Value, fmt.Sprintf("%s.unitName", fieldName), 0, &err)
				mapped.UnitName = f.UnitName.Value
			}
		} else if param.Bool != nil {
			mapped.Type = orcapi.ApplicationParameterTypeBoolean
			b := param.Bool
			base = &param.Bool.A1ParamBase
			if base.DefaultValue.Present {
				_, jsDefault := a1ReadDefaultValue[bool](&base.DefaultValue.Value, fieldName, &err)
				mapped.DefaultValue = jsDefault
			}

			mapped.TrueValue = b.TrueValue
			mapped.FalseValue = b.FalseValue

			if mapped.TrueValue == "" {
				mapped.TrueValue = "true"
			}
			if mapped.FalseValue == "" {
				mapped.FalseValue = "false"
			}
		} else if param.Enumeration != nil {
			mapped.Type = orcapi.ApplicationParameterTypeEnumeration
			e := param.Enumeration
			base = &param.Enumeration.A1ParamBase

			if base.DefaultValue.Present {
				defaultValue, jsDefault := a1ReadDefaultValue[string](&base.DefaultValue.Value, fieldName, &err)
				mapped.DefaultValue = jsDefault

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
						fieldName,
					))
				}
			}

			for i, opt := range e.Options {
				util.ValidateString(&opt.Name, fmt.Sprintf("%s.options[%v].name", fieldName, i), 0, &err)
				util.ValidateString(&opt.Value, fmt.Sprintf("%s.options[%v].value", fieldName, i), 0, &err)

				mapped.Options = append(mapped.Options, orcapi.EnumOption{
					Name:  opt.Name,
					Value: opt.Value,
				})
			}
		} else if param.Peer != nil {
			mapped.Type = orcapi.ApplicationParameterTypePeer
			base = &param.Peer.A1ParamBase
			if base.DefaultValue.Present {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.LicenseServer != nil {
			mapped.Type = orcapi.ApplicationParameterTypeLicenseServer
			base = &param.LicenseServer.A1ParamBase
			if base.DefaultValue.Present {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.Ingress != nil {
			mapped.Type = orcapi.ApplicationParameterTypeIngress
			base = &param.Ingress.A1ParamBase
			if base.DefaultValue.Present {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.NetworkIP != nil {
			mapped.Type = orcapi.ApplicationParameterTypeNetworkIp
			base = &param.NetworkIP.A1ParamBase
			if base.DefaultValue.Present {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else {
			err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest,
				"unrecognized parameter type %s", param.Type))
			break
		}

		base.Name = name

		util.ValidateString(&base.Name, fieldName, 0, &err)
		util.ValidateString(&base.Description, fmt.Sprintf("%s.description", fieldName),
			util.StringValidationAllowMultiline|util.StringValidationAllowEmpty, &err)
		if !base.Title.Present {
			base.Title.Set(base.Name)
		} else {
			util.ValidateString(&base.Title.Value, fmt.Sprintf("%s.title", fieldName), 0, &err)
		}

		mapped.Name = name
		mapped.Title = base.Title.GetOrDefault("")
		mapped.Optional = base.Optional
		mapped.Description = base.Description
		mappedParameters = append(mappedParameters, mapped)
	}

	var invocation []orcapi.InvocationParameter

	for i, invocationParam := range y.Invocation {
		paramName := fmt.Sprintf("invocation[%d]", i)
		y.validateInvocationParam(invocationParam.ToParameter(), paramName, &err)
		invocation = append(invocation, invocationParam.ToParameter())
	}

	environment := map[string]orcapi.InvocationParameter{}

	for name, value := range y.Environment {
		paramName := fmt.Sprintf("environment[%s]", name)
		util.ValidateString(&name, paramName, 0, &err)
		y.validateInvocationParam(value.ToParameter(), paramName, &err)
		environment[name] = value.ToParameter()
	}

	// NOTE(Dan): vnc and web nothing to check
	// NOTE(Dan): globs are ignored
	// NOTE(Dan): container nothing to check

	if err != nil {
		return orcapi.Application{}, err
	} else {
		app := orcapi.Application{
			WithAppMetadata: orcapi.WithAppMetadata{
				Metadata: orcapi.ApplicationMetadata{
					NameAndVersion: orcapi.NameAndVersion{
						Name:    y.Name,
						Version: y.Version,
					},
					Authors:     []string{"UCloud"},
					Title:       y.Title,
					Description: y.Description,
					Website:     y.Website.GetOrDefault(""),
					Public:      false,
					FlavorName:  util.OptNone[string](),
					Group:       orcapi.ApplicationGroup{},
					CreatedAt:   fndapi.Timestamp(time.Now()),
				},
			},
			WithAppInvocation: orcapi.WithAppInvocation{
				Invocation: orcapi.ApplicationInvocationDescription{
					Tool: orcapi.ToolReference{
						NameAndVersion: y.Tool,
					},
					Invocation:      invocation,
					Parameters:      mappedParameters,
					OutputFileGlobs: []string{"*"},
					ApplicationType: y.ApplicationType.GetOrDefault(""),
					Vnc:             y.Vnc,
					Web:             y.Web,
					Ssh:             y.Ssh,
					Container: y.Container.GetOrDefault(orcapi.ContainerDescription{
						ChangeWorkingDirectory: true,
						RunAsRoot:              true,
						RunAsRealUser:          false,
					}),
					Environment:           environment,
					AllowAdditionalMounts: y.AllowAdditionalMounts,
					AllowAdditionalPeers:  y.AllowAdditionalPeers,
					AllowMultiNode:        y.AllowMultiNode,
					AllowPublicIp:         y.AllowPublicIp,
					AllowPublicLink:       y.AllowPublicLink,
					FileExtensions:        y.FileExtensions,
					LicenseServers:        y.LicenseServers,
					Modules: orcapi.ModulesSection{
						MountPath: y.Modules.Value.MountPath,
						Optional:  y.Modules.Value.Optional,
					},
					Sbatch: map[string]orcapi.InvocationParameter{},
				},
			},
		}

		return app, nil
	}
}

func (y *A1Yaml) validateInvocationParam(
	invocationParam orcapi.InvocationParameter,
	paramName string,
	err **util.HttpError,
) {
	switch invocationParam.Type {
	case orcapi.InvocationParameterTypeEnv:
		e := invocationParam.InvocationParameterEnv
		util.ValidateString(&e.Variable, paramName, 0, err)

	case orcapi.InvocationParameterTypeWord:
		w := invocationParam.InvocationParameterWord
		util.ValidateString(&w.Word, paramName, util.StringValidationAllowMultiline, err)

	case orcapi.InvocationParameterTypeVar:
		v := invocationParam.InvocationParameterVar
		if len(v.VariableNames) == 0 {
			*err = util.MergeHttpErr(*err, util.HttpErr(
				http.StatusBadRequest,
				"%s.variableNames must not be empty",
				paramName,
			))
		}

		for _, varName := range v.VariableNames {
			_, ok := y.Parameters[varName]
			if !ok {
				*err = util.MergeHttpErr(*err, util.HttpErr(
					http.StatusBadRequest,
					"%s variable '%s' does not exist",
					paramName,
					varName,
				))
			}
		}

	case orcapi.InvocationParameterTypeBoolFlag, "flag":
		b := invocationParam.InvocationParameterBoolFlag
		_, ok := y.Parameters[b.VariableName]
		if !ok {
			*err = util.MergeHttpErr(*err, util.HttpErr(
				http.StatusBadRequest,
				"%s variable '%s' does not exist",
				paramName,
				b.VariableName,
			))
		}

	case orcapi.InvocationParameterTypeJinja:
		// Nothing to check
	}
}

func (y *A1Tool) Normalize() (orcapi.ToolReference, *util.HttpError) {
	var err *util.HttpError
	util.ValidateString(&y.Name, "name", 0, &err)
	util.ValidateString(&y.Version, "version", 0, &err)
	util.ValidateString(&y.Title, "title", 0, &err)
	if y.Container.Present {
		util.ValidateString(&y.Container.Value, "container", 0, &err)
	}
	util.ValidateEnum(&y.Backend,
		[]orcapi.ToolBackend{orcapi.ToolBackendNative, orcapi.ToolBackendVirtualMachine, orcapi.ToolBackendDocker},
		"backend", &err)

	if err != nil {
		return orcapi.ToolReference{}, err
	} else {
		return orcapi.ToolReference{
			NameAndVersion: orcapi.NameAndVersion{
				Name:    y.Name,
				Version: y.Version,
			},
			Tool: util.OptValue(orcapi.Tool{
				Owner:     "UCloud",
				CreatedAt: fndapi.Timestamp(time.Now()),
				Description: orcapi.ToolDescription{
					Info: orcapi.NameAndVersion{
						Name:    y.Name,
						Version: y.Version,
					},
					DefaultNumberOfNodes:  y.DefaultNumberOfNodes.GetOrDefault(1),
					DefaultTimeAllocation: y.DefaultTimeAllocation.GetOrDefault(orcapi.SimpleDuration{Hours: 1}),
					Authors:               []string{"UCloud"},
					Title:                 y.Title,
					Description:           y.Description,
					Backend:               y.Backend,
					License:               y.License,
					Image:                 y.Image.GetOrDefault(y.Container.Value),
					Container:             y.Container.GetOrDefault(y.Image.Value),
				},
			}),
		}, nil
	}
}
