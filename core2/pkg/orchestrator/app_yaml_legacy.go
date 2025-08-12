package orchestrator

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"net/http"
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
	Name         string              `yaml:"name"`
	Optional     bool                `yaml:"optional"`
	Title        util.Option[string] `yaml:"title"`
	Description  string              `yaml:"description"`
	DefaultValue *yaml.Node          `yaml:"defaultValue"`
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
	Invocation            []orcapi.InvocationParameter             `yaml:"invocation"`
	Parameters            map[string]A1ApplicationParameterYaml    `yaml:"parameters"`
	OutputFileGlobs       []string                                 `yaml:"outputFileGlobs"`
	ApplicationType       util.Option[orcapi.ApplicationType]      `yaml:"applicationType"`
	Vnc                   util.Option[orcapi.VncDescription]       `yaml:"vnc"`
	Web                   util.Option[orcapi.WebDescription]       `yaml:"web"`
	Ssh                   util.Option[orcapi.SshDescription]       `yaml:"ssh"`
	Container             util.Option[orcapi.ContainerDescription] `yaml:"container"`
	Environment           map[string]orcapi.InvocationParameter    `yaml:"environment"`
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

type A1Module struct {
	MountPath string   `yaml:"mountPath"`
	Optional  []string `yaml:"optional"`
}

// Normalization
// =====================================================================================================================

func a1ReadDefaultValue[T any](n *yaml.Node, fieldName string, outErr **util.HttpError) T {
	type wrapper struct {
		Value T `yaml:"value"`
	}

	var w wrapper
	err := n.Decode(&w)
	if err == nil {
		return w.Value
	} else {
		var t T
		err = n.Decode(&t)
		if err != nil {
			*outErr = util.HttpErr(http.StatusBadRequest, "%s invalid default value", fieldName)
		}
		return t
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

	appType := y.ApplicationType.Value

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

	for name, param := range y.Parameters {
		fieldName := fmt.Sprintf("parameters[%v]", name)

		var base *A1ParamBase
		if param.InputFile != nil {
			base = &param.InputFile.A1ParamBase
			if base.DefaultValue != nil {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.InputDirectory != nil {
			base = &param.InputDirectory.A1ParamBase
			if base.DefaultValue != nil {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.Text != nil {
			base = &param.Text.A1ParamBase
			if base.DefaultValue != nil {
				a1ReadDefaultValue[string](base.DefaultValue, fieldName, &err)
			}
		} else if param.TextArea != nil {
			base = &param.TextArea.A1ParamBase
			if base.DefaultValue != nil {
				a1ReadDefaultValue[string](base.DefaultValue, fieldName, &err)
			}
		} else if param.Integer != nil {
			base = &param.Integer.A1ParamBase
			if base.DefaultValue != nil {
				a1ReadDefaultValue[int](base.DefaultValue, fieldName, &err)
			}

			// todo check min, max, step and unit name
		} else if param.FloatingPoint != nil {
			base = &param.FloatingPoint.A1ParamBase
			if base.DefaultValue != nil {
				a1ReadDefaultValue[float64](base.DefaultValue, fieldName, &err)
			}

			// todo check min, max, step and unit name
		} else if param.Bool != nil {
			base = &param.Bool.A1ParamBase
			if base.DefaultValue != nil {
				a1ReadDefaultValue[bool](base.DefaultValue, fieldName, &err)
			}
		} else if param.Enumeration != nil {
			base = &param.Enumeration.A1ParamBase
			if base.DefaultValue != nil {
				a1ReadDefaultValue[string](base.DefaultValue, fieldName, &err)
				// TODO check if present in values
			}
			// todo check options
		} else if param.Peer != nil {
			base = &param.Peer.A1ParamBase
			if base.DefaultValue != nil {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.LicenseServer != nil {
			base = &param.LicenseServer.A1ParamBase
			if base.DefaultValue != nil {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.Ingress != nil {
			base = &param.Ingress.A1ParamBase
			if base.DefaultValue != nil {
				err = util.MergeHttpErr(err,
					util.HttpErr(http.StatusBadRequest, "%s must not have a defaultValue", fieldName))
			}
		} else if param.NetworkIP != nil {
			base = &param.NetworkIP.A1ParamBase
			if base.DefaultValue != nil {
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

	}

	// NOTE(Dan): vnc and web nothing to check
	// NOTE(Dan): globs are ignored

	// TODO invocation
	// TODO container
	// TODO environment

	return orcapi.Application{}, err
}
