package orchestrator

import (
	"fmt"
	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/util"
)

type ApplicationYamlV2 struct {
	Name          string                  `yaml:"name"`
	Version       string                  `yaml:"version"`
	Software      A2Software              `yaml:"software"`
	Title         util.Option[string]     `yaml:"title"`
	Description   util.Option[string]     `yaml:"description"`
	License       util.Option[string]     `yaml:"license"`
	Documentation util.Option[string]     `yaml:"documentation"`
	Features      util.Option[A2Features] `yaml:"features"`
	Modules       util.Option[A2Module]   `yaml:"modules"`
	Parameters    map[string]A2Parameter  `yaml:"parameters"`
	Sbatch        map[string]string       `yaml:"sbatch"`
	Invocation    string                  `yaml:"invocation"`
	Environment   map[string]string       `yaml:"environment"`
	Web           util.Option[A2Web]      `yaml:"web"`
	Vnc           util.Option[A2Vnc]      `yaml:"vnc"`
	Ssh           util.Option[A2Ssh]      `yaml:"ssh"`
	Extensions    []string                `yaml:"extensions"`
}

type A2Software struct {
	Type           string                    `yaml:"type"`
	Native         *A2NativeSoftware         `yaml:"-"`
	Container      *A2ContainerSoftware      `yaml:"-"`
	VirtualMachine *A2VirtualMachineSoftware `yaml:"-"`
}

type A2NativeSoftware struct {
	Type string                `yaml:"type"`
	Load []A2ApplicationToLoad `yaml:"load"`
}

type A2ApplicationToLoad struct {
	Name    string `yaml:"name"`
	Version string `yaml:"version"`
}

type A2ContainerSoftware struct {
	Type  string `yaml:"type"`
	Image string `yaml:"image"`
}

type A2VirtualMachineSoftware struct {
	Type  string `yaml:"type"`
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
		v.Type = "Native"
		s.Native = &v
		return nil
	case "Container":
		var v A2ContainerSoftware
		if err := n.Decode(&v); err != nil {
			return err
		}
		s.Type = "Container"
		v.Type = "Container"
		s.Container = &v
		return nil
	case "VirtualMachine":
		var v A2VirtualMachineSoftware
		if err := n.Decode(&v); err != nil {
			return err
		}
		s.Type = "VirtualMachine"
		v.Type = "VirtualMachine"
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

func (b *A2ParamBase) UnmarshalYAML(n *yaml.Node) error {
	type alias struct {
		Title       string `yaml:"title"`
		Description string `yaml:"description"`
		Optional    *bool  `yaml:"optional"`
	}
	var a alias
	if err := n.Decode(&a); err != nil {
		return err
	}
	b.Title = a.Title
	b.Description = a.Description
	if a.Optional == nil {
		b.Optional = true
	} else {
		b.Optional = *a.Optional
	}
	return nil
}

type A2ParamFile struct {
	A2ParamBase
	Type string `yaml:"type"`
}

type A2ParamDirectory struct {
	A2ParamBase
	Type string `yaml:"type"`
}

type A2ParamLicense struct {
	A2ParamBase
	Type string `yaml:"type"`
}

type A2ParamJob struct {
	A2ParamBase
	Type string `yaml:"type"`
}

type A2ParamPublicIp struct {
	A2ParamBase
	Type string `yaml:"type"`
}

type A2ParamInt struct {
	A2ParamBase
	Type         string             `yaml:"type"`
	DefaultValue util.Option[int64] `yaml:"defaultValue"`
	Min          util.Option[int64] `yaml:"min"`
	Max          util.Option[int64] `yaml:"max"`
	Step         util.Option[int64] `yaml:"step"`
}

type A2ParamFloat struct {
	A2ParamBase
	Type         string               `yaml:"type"`
	DefaultValue util.Option[float64] `yaml:"defaultValue"`
	Min          util.Option[float64] `yaml:"min"`
	Max          util.Option[float64] `yaml:"max"`
	Step         util.Option[float64] `yaml:"step"`
}

type A2ParamBool struct {
	A2ParamBase
	Type         string            `yaml:"type"`
	DefaultValue util.Option[bool] `yaml:"defaultValue"`
}

type A2ParamText struct {
	A2ParamBase
	Type         string              `yaml:"type"`
	DefaultValue util.Option[string] `yaml:"defaultValue"`
}

type A2ParamTextArea struct {
	A2ParamBase
	Type         string              `yaml:"type"`
	DefaultValue util.Option[string] `yaml:"defaultValue"`
}

type A2EnumOption struct {
	Title string `yaml:"title"`
	Value string `yaml:"value"`
}

type A2ParamEnum struct {
	A2ParamBase
	Type         string              `yaml:"type"`
	DefaultValue util.Option[string] `yaml:"defaultValue"` // references the value
	Options      []A2EnumOption      `yaml:"options"`
}

type A2ParamWorkflow struct {
	A2ParamBase
	Type       string                 `yaml:"type"`
	Init       util.Option[string]    `yaml:"init"`
	Job        util.Option[string]    `yaml:"job"`
	Readme     util.Option[string]    `yaml:"readme"`
	Parameters map[string]A2Parameter `yaml:"parameters"`
}

func (p *A2ParamWorkflow) UnmarshalYAML(n *yaml.Node) error {
	type alias struct {
		A2ParamBase `yaml:",inline"`
		Type        string                 `yaml:"type"`
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
		v.Type = "File"
		p.Type = "File"
		p.File = &v
	case "Directory":
		var v A2ParamDirectory
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Directory"
		p.Type = "Directory"
		p.Directory = &v
	case "License":
		var v A2ParamLicense
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "License"
		p.Type = "License"
		p.License = &v
	case "Job":
		var v A2ParamJob
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Job"
		p.Type = "Job"
		p.Job = &v
	case "PublicIP":
		var v A2ParamPublicIp
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "PublicIP"
		p.Type = "PublicIP"
		p.PublicIP = &v
	case "Integer":
		var v A2ParamInt
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Integer"
		p.Type = "Integer"
		p.Integer = &v
	case "FloatingPoint":
		var v A2ParamFloat
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "FloatingPoint"
		p.Type = "FloatingPoint"
		p.FloatingPoint = &v
	case "Boolean":
		var v A2ParamBool
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Boolean"
		p.Type = "Boolean"
		p.Boolean = &v
	case "Text":
		var v A2ParamText
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Text"
		p.Type = "Text"
		p.Text = &v
	case "TextArea":
		var v A2ParamTextArea
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "TextArea"
		p.Type = "TextArea"
		p.TextArea = &v
	case "Enumeration":
		var v A2ParamEnum
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Enumeration"
		p.Type = "Enumeration"
		p.Enumeration = &v
	case "Workflow":
		var v A2ParamWorkflow
		if err := n.Decode(&v); err != nil {
			return err
		}
		v.Type = "Workflow"
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

type A2Module struct {
	MountPath string   `yaml:"mountPath"`
	Optional  []string `yaml:"optional"`
}
