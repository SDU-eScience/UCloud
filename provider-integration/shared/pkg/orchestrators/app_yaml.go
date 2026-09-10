package orchestrators

import (
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type A2Yaml struct {
	Name            string                      `yaml:"name"`
	Version         string                      `yaml:"version"`
	Software        A2Software                  `yaml:"software"`
	Title           util.Option[string]         `yaml:"title"`
	Description     util.Option[string]         `yaml:"description"`
	License         util.Option[string]         `yaml:"license"`
	Documentation   util.Option[string]         `yaml:"documentation"`
	Features        util.Option[A2Features]     `yaml:"features"`
	Modules         util.Option[A2Module]       `yaml:"modules"`
	Parameters      map[string]A2Parameter      `yaml:"parameters"`
	ParametersOrder []string                    `yaml:"-"` // needed to preserve YAML declaration order
	Sbatch          map[string]string           `yaml:"sbatch"`
	Invocation      string                      `yaml:"invocation"`
	Ucx             util.Option[UcxDescription] `yaml:"ucx"`
	Environment     map[string]string           `yaml:"environment"`
	Web             util.Option[A2Web]          `yaml:"web"`
	Vnc             util.Option[A2Vnc]          `yaml:"vnc"`
	Ssh             util.Option[A2Ssh]          `yaml:"ssh"`
	Inference       util.Option[A2Inference]    `yaml:"inference"`
	Extensions      []string                    `yaml:"extensions"`
}

type A2SoftwareKind string

const (
	A2SoftwareNative         A2SoftwareKind = "Native"
	A2SoftwareContainer      A2SoftwareKind = "Container"
	A2SoftwareVirtualMachine A2SoftwareKind = "VirtualMachine"
	A2SoftwareUcx            A2SoftwareKind = "UCX"
)

var A2SoftwareKinds = []A2SoftwareKind{
	A2SoftwareNative,
	A2SoftwareContainer,
	A2SoftwareVirtualMachine,
	A2SoftwareUcx,
}

type A2Software struct {
	Type           A2SoftwareKind            `yaml:"type"`
	Native         *A2NativeSoftware         `yaml:"-"`
	Container      *A2ContainerSoftware      `yaml:"-"`
	VirtualMachine *A2VirtualMachineSoftware `yaml:"-"`
	Ucx            *A2UcxSoftware            `yaml:"-"`
}

type A2NativeSoftware struct {
	Load []A2ApplicationToLoad `json:"load" yaml:"load"`
}

type A2ApplicationToLoad struct {
	Name    string `json:"name" yaml:"name"`
	Version string `json:"version" yaml:"version"`
}

type A2ContainerSoftware struct {
	Image string `json:"image" yaml:"image"`
}

type A2VirtualMachineSoftware struct {
	Image string `json:"image" yaml:"image"`
}

type A2UcxSoftware struct {
	Image string `json:"image" yaml:"image"`
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
	case "UCX":
		var v A2UcxSoftware
		if err := n.Decode(&v); err != nil {
			return err
		}
		s.Type = "UCX"
		s.Ucx = &v
		return nil

	default:
		return fmt.Errorf("unknown software type: %q", t.Type)
	}
}

func (s *A2Software) UnmarshalJSON(data []byte) error {
	var kind struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(data, &kind); err != nil {
		return err
	}
	switch kind.Type {
	case "Native":
		var value A2NativeSoftware
		if err := json.Unmarshal(data, &value); err != nil {
			return err
		}
		s.Type, s.Native = A2SoftwareNative, &value
	case "Container":
		var value A2ContainerSoftware
		if err := json.Unmarshal(data, &value); err != nil {
			return err
		}
		s.Type, s.Container = A2SoftwareContainer, &value
	case "VirtualMachine":
		var value A2VirtualMachineSoftware
		if err := json.Unmarshal(data, &value); err != nil {
			return err
		}
		s.Type, s.VirtualMachine = A2SoftwareVirtualMachine, &value
	case "UCX":
		var value A2UcxSoftware
		if err := json.Unmarshal(data, &value); err != nil {
			return err
		}
		s.Type, s.Ucx = A2SoftwareUcx, &value
	default:
		return fmt.Errorf("unknown software type: %q", kind.Type)
	}
	return nil
}

func a2MarshalTagged(kind string, payload any) ([]byte, error) {
	value := map[string]any{"type": kind}
	if payload != nil {
		raw, err := json.Marshal(payload)
		if err != nil {
			return nil, err
		}
		if err = json.Unmarshal(raw, &value); err != nil {
			return nil, err
		}
		value["type"] = kind
	}
	return json.Marshal(value)
}

func a2MarshalTaggedYaml(kind string, payload any) (any, error) {
	raw, err := a2MarshalTagged(kind, payload)
	if err != nil {
		return nil, err
	}
	var value map[string]any
	err = json.Unmarshal(raw, &value)
	return value, err
}

func (s A2Software) MarshalJSON() ([]byte, error) {
	switch s.Type {
	case A2SoftwareNative:
		return a2MarshalTagged(string(s.Type), s.Native)
	case A2SoftwareContainer:
		return a2MarshalTagged(string(s.Type), s.Container)
	case A2SoftwareVirtualMachine:
		return a2MarshalTagged(string(s.Type), s.VirtualMachine)
	case A2SoftwareUcx:
		return a2MarshalTagged(string(s.Type), s.Ucx)
	default:
		return a2MarshalTagged(string(s.Type), nil)
	}
}

func (s A2Software) MarshalYAML() (any, error) {
	switch s.Type {
	case A2SoftwareNative:
		return a2MarshalTaggedYaml(string(s.Type), s.Native)
	case A2SoftwareContainer:
		return a2MarshalTaggedYaml(string(s.Type), s.Container)
	case A2SoftwareVirtualMachine:
		return a2MarshalTaggedYaml(string(s.Type), s.VirtualMachine)
	case A2SoftwareUcx:
		return a2MarshalTaggedYaml(string(s.Type), s.Ucx)
	default:
		return a2MarshalTaggedYaml(string(s.Type), nil)
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
	Title       string `json:"title" yaml:"title"`
	Description string `json:"description" yaml:"description"`
	Optional    bool   `json:"optional" yaml:"optional"`
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
	DefaultValue util.Option[int64] `json:"defaultValue" yaml:"defaultValue"`
	Min          util.Option[int64] `json:"min" yaml:"min"`
	Max          util.Option[int64] `json:"max" yaml:"max"`
	Step         util.Option[int64] `json:"step" yaml:"step"`
}

type A2ParamFloat struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[float64] `json:"defaultValue" yaml:"defaultValue"`
	Min          util.Option[float64] `json:"min" yaml:"min"`
	Max          util.Option[float64] `json:"max" yaml:"max"`
	Step         util.Option[float64] `json:"step" yaml:"step"`
}

type A2ParamBool struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[bool] `json:"defaultValue" yaml:"defaultValue"`
}

type A2ParamText struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[string] `json:"defaultValue" yaml:"defaultValue"`
}

type A2ParamTextArea struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[string] `json:"defaultValue" yaml:"defaultValue"`
}

type A2EnumOption struct {
	Title string `json:"title" yaml:"title"`
	Value string `json:"value" yaml:"value"`
}

type A2ParamEnum struct {
	A2ParamBase  `yaml:",inline"`
	DefaultValue util.Option[string] `json:"defaultValue" yaml:"defaultValue"` // references the value
	Options      []A2EnumOption      `json:"options" yaml:"options"`
}

type A2ParamWorkflow struct {
	A2ParamBase `yaml:",inline"`
	Init        util.Option[string]    `json:"init" yaml:"init"`
	Job         util.Option[string]    `json:"job" yaml:"job"`
	Readme      util.Option[string]    `json:"readme" yaml:"readme"`
	Parameters  map[string]A2Parameter `json:"parameters" yaml:"parameters"`
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

func (p *A2Parameter) UnmarshalJSON(data []byte) error {
	var kind struct {
		Type string `json:"type"`
	}
	if err := json.Unmarshal(data, &kind); err != nil {
		return err
	}
	p.Type = kind.Type
	switch kind.Type {
	case "File":
		p.File = &A2ParamFile{}
		return json.Unmarshal(data, p.File)
	case "Directory":
		p.Directory = &A2ParamDirectory{}
		return json.Unmarshal(data, p.Directory)
	case "License":
		p.License = &A2ParamLicense{}
		return json.Unmarshal(data, p.License)
	case "Job":
		p.Job = &A2ParamJob{}
		return json.Unmarshal(data, p.Job)
	case "PublicIP":
		p.PublicIP = &A2ParamPublicIp{}
		return json.Unmarshal(data, p.PublicIP)
	case "Integer":
		p.Integer = &A2ParamInt{}
		return json.Unmarshal(data, p.Integer)
	case "FloatingPoint":
		p.FloatingPoint = &A2ParamFloat{}
		return json.Unmarshal(data, p.FloatingPoint)
	case "Boolean":
		p.Boolean = &A2ParamBool{}
		return json.Unmarshal(data, p.Boolean)
	case "Text":
		p.Text = &A2ParamText{}
		return json.Unmarshal(data, p.Text)
	case "TextArea":
		p.TextArea = &A2ParamTextArea{}
		return json.Unmarshal(data, p.TextArea)
	case "Enumeration":
		p.Enumeration = &A2ParamEnum{}
		return json.Unmarshal(data, p.Enumeration)
	case "Workflow":
		p.Workflow = &A2ParamWorkflow{}
		return json.Unmarshal(data, p.Workflow)
	default:
		return fmt.Errorf("unknown parameter type: %q", kind.Type)
	}
}

func (p A2Parameter) payload() any {
	switch p.Type {
	case "File":
		return p.File
	case "Directory":
		return p.Directory
	case "License":
		return p.License
	case "Job":
		return p.Job
	case "PublicIP":
		return p.PublicIP
	case "Integer":
		return p.Integer
	case "FloatingPoint":
		return p.FloatingPoint
	case "Boolean":
		return p.Boolean
	case "Text":
		return p.Text
	case "TextArea":
		return p.TextArea
	case "Enumeration":
		return p.Enumeration
	case "Workflow":
		return p.Workflow
	default:
		return nil
	}
}

func (p A2Parameter) MarshalJSON() ([]byte, error) {
	return a2MarshalTagged(p.Type, p.payload())
}

func (p A2Parameter) MarshalYAML() (any, error) {
	return a2MarshalTaggedYaml(p.Type, p.payload())
}

type A2Features struct {
	MultiNode   bool              `json:"multiNode" yaml:"multiNode"`
	Links       util.Option[bool] `json:"links" yaml:"links"`
	IPAddresses util.Option[bool] `json:"ipAddresses" yaml:"ipAddresses"`
	Folders     util.Option[bool] `json:"folders" yaml:"folders"`
	JobLinking  util.Option[bool] `json:"jobLinking" yaml:"jobLinking"`
	JobAuditLog util.Option[bool] `json:"jobAuditLog" yaml:"jobAuditLog"`
}

type A2Web struct {
	Enabled bool             `json:"enabled" yaml:"enabled"`
	Port    util.Option[int] `json:"port" yaml:"port"`
}

type A2Vnc struct {
	Enabled  bool                `json:"enabled" yaml:"enabled"`
	Port     util.Option[int]    `json:"port" yaml:"port"`
	Password util.Option[string] `json:"password" yaml:"password"`
}

type A2Ssh struct {
	Mode A2SshMode `json:"mode" yaml:"mode"`
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

type A2Inference struct {
	Mode A2InferenceMode `json:"mode" yaml:"mode"`
}

type A2InferenceMode string

const (
	A2InferenceModeNone      A2InferenceMode = "None"
	A2InferenceModeOptional  A2InferenceMode = "Optional"
	A2InferenceModeMandatory A2InferenceMode = "Mandatory"
)

var A2InferenceModeOptions = []A2InferenceMode{
	A2InferenceModeNone,
	A2InferenceModeOptional,
	A2InferenceModeMandatory,
}

type A2Module struct {
	MountPath string   `json:"mountPath" yaml:"mountPath"`
	Optional  []string `json:"optional" yaml:"optional"`
}

func a2YamlMappingValue(node *yaml.Node, key string) *yaml.Node {
	if node == nil || node.Kind != yaml.MappingNode {
		return nil
	}
	for i := 0; i+1 < len(node.Content); i += 2 {
		if node.Content[i].Value == key {
			return node.Content[i+1]
		}
	}
	return nil
}

func a2YamlValidateFields(node *yaml.Node, path string, allowed ...string) error {
	if node == nil || node.Kind != yaml.MappingNode {
		return nil
	}
	for i := 0; i+1 < len(node.Content); i += 2 {
		name := node.Content[i].Value
		if slices.Contains(allowed, name) {
			continue
		}
		if path == "" {
			return fmt.Errorf("unknown field %q", name)
		}
		return fmt.Errorf("unknown field %q", path+"."+name)
	}
	return nil
}

func a2YamlValidateParameterFields(node *yaml.Node, path string) error {
	typeNode := a2YamlMappingValue(node, "type")
	if typeNode == nil {
		return a2YamlValidateFields(node, path, "type", "title", "description", "optional")
	}
	base := []string{"type", "title", "description", "optional"}
	switch typeNode.Value {
	case "Integer", "FloatingPoint":
		base = append(base, "defaultValue", "min", "max", "step")
	case "Boolean", "Text", "TextArea":
		base = append(base, "defaultValue")
	case "Enumeration":
		base = append(base, "defaultValue", "options")
	case "Workflow":
		base = append(base, "init", "job", "readme", "parameters")
	}
	if err := a2YamlValidateFields(node, path, base...); err != nil {
		return err
	}
	if typeNode.Value == "Enumeration" {
		options := a2YamlMappingValue(node, "options")
		if options != nil && options.Kind == yaml.SequenceNode {
			for i, option := range options.Content {
				if err := a2YamlValidateFields(option, fmt.Sprintf("%s.options[%d]", path, i), "title", "value"); err != nil {
					return err
				}
			}
		}
	}
	if typeNode.Value == "Workflow" {
		parameters := a2YamlMappingValue(node, "parameters")
		if parameters != nil && parameters.Kind == yaml.MappingNode {
			for i := 0; i+1 < len(parameters.Content); i += 2 {
				name := parameters.Content[i].Value
				if err := a2YamlValidateParameterFields(parameters.Content[i+1], path+".parameters."+name); err != nil {
					return err
				}
			}
		}
	}
	return nil
}

func a2YamlValidateDocumentFields(node *yaml.Node) error {
	if err := a2YamlValidateFields(
		node,
		"",
		"application", "name", "version", "software", "title", "description", "license", "documentation",
		"features", "modules", "parameters", "sbatch", "invocation", "ucx", "environment", "web", "vnc", "ssh",
		"inference", "extensions",
	); err != nil {
		return err
	}

	software := a2YamlMappingValue(node, "software")
	softwareType := a2YamlMappingValue(software, "type")
	if softwareType != nil && softwareType.Value == "Native" {
		if err := a2YamlValidateFields(software, "software", "type", "load"); err != nil {
			return err
		}
		load := a2YamlMappingValue(software, "load")
		if load != nil && load.Kind == yaml.SequenceNode {
			for i, application := range load.Content {
				if err := a2YamlValidateFields(application, fmt.Sprintf("software.load[%d]", i), "name", "version"); err != nil {
					return err
				}
			}
		}
	} else if err := a2YamlValidateFields(software, "software", "type", "image"); err != nil {
		return err
	}

	parameters := a2YamlMappingValue(node, "parameters")
	if parameters != nil && parameters.Kind == yaml.MappingNode {
		for i := 0; i+1 < len(parameters.Content); i += 2 {
			name := parameters.Content[i].Value
			if err := a2YamlValidateParameterFields(parameters.Content[i+1], "parameters."+name); err != nil {
				return err
			}
		}
	}

	sections := []struct {
		Name   string
		Fields []string
	}{
		{Name: "features", Fields: []string{"multiNode", "links", "ipAddresses", "folders", "jobLinking", "jobAuditLog"}},
		{Name: "modules", Fields: []string{"mountPath", "optional"}},
		{Name: "web", Fields: []string{"enabled", "port"}},
		{Name: "vnc", Fields: []string{"enabled", "port", "password"}},
		{Name: "ssh", Fields: []string{"mode"}},
		{Name: "inference", Fields: []string{"mode"}},
		{Name: "ucx", Fields: []string{"executable"}},
	}
	for _, section := range sections {
		if err := a2YamlValidateFields(a2YamlMappingValue(node, section.Name), section.Name, section.Fields...); err != nil {
			return err
		}
	}
	executable := a2YamlMappingValue(a2YamlMappingValue(node, "ucx"), "executable")
	return a2YamlValidateFields(executable, "ucx.executable", "manifestUrl", "publicKey", "binaryName")
}

func (y *A2Yaml) UnmarshalYAML(n *yaml.Node) error {
	type alias A2Yaml
	var a alias
	if err := a2YamlValidateDocumentFields(n); err != nil {
		return err
	}

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

func (y *A2Yaml) UnmarshalJSON(data []byte) error {
	type alias A2Yaml
	var value alias
	if err := json.Unmarshal(data, &value); err != nil {
		return err
	}
	value.ParametersOrder = make([]string, 0, len(value.Parameters))
	for name := range value.Parameters {
		value.ParametersOrder = append(value.ParametersOrder, name)
	}
	slices.Sort(value.ParametersOrder)
	*y = A2Yaml(value)
	return nil
}

func (y *A2Yaml) Normalize() (Application, *util.HttpError) {
	var err *util.HttpError
	var mappedParameters []ApplicationParameter
	mappedEnvironment := map[string]InvocationParameter{}
	mappedSbatch := map[string]InvocationParameter{}
	mappedAppType := ApplicationTypeBatch
	mappedModules := util.OptNone[ModulesSection]()

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
	if y.License.Present {
		util.ValidateString(&y.License.Value, "license", 0, &err)
	}
	util.ValidateString(
		&y.Invocation,
		"invocation",
		util.StringValidationAllowMultiline|util.StringValidationAllowLong,
		&err,
	)
	for i := 0; i < len(y.Extensions); i++ {
		util.ValidateString(&y.Extensions[i], fmt.Sprintf("extensions[%d]", i), 0, &err)
	}

	mappedTool := ToolDescription{
		Info: NameAndVersion{
			Name:    y.Name,
			Version: y.Version,
		},
		DefaultNumberOfNodes:  1,
		DefaultTimeAllocation: SimpleDuration{Hours: 1},
		RequiredModules:       nil,
		Authors:               []string{"UCloud"},
		Title:                 y.Title.GetOrDefault(y.Name),
		Description:           y.Description.GetOrDefault(""),
		License:               y.License.GetOrDefault(""),
	}
	util.ValidateEnum(&y.Software.Type, A2SoftwareKinds, "software.type", &err)
	switch y.Software.Type {
	case A2SoftwareContainer:
		mappedTool.Backend = ToolBackendDocker

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
		mappedTool.Backend = ToolBackendVirtualMachine

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
		mappedTool.Backend = ToolBackendNative

		if y.Software.Native == nil {
			err = util.MergeHttpErr(err, util.HttpErr(
				http.StatusBadRequest,
				"missing native information in 'software'",
			))
		} else {
			instr := ToolLoadInstructions{
				Type: ToolLoadInstructionsNative,
			}

			modulesToLoad := y.Software.Native.Load
			for i := 0; i < len(modulesToLoad); i++ {
				mod := &modulesToLoad[i]
				util.ValidateString(&mod.Name, fmt.Sprintf("software.load[%d].name", i), 0, &err)
				util.ValidateString(&mod.Version, fmt.Sprintf("software.load[%d].version", i), 0, &err)

				instr.Applications = append(instr.Applications, NativeApplication{
					Name:    mod.Name,
					Version: mod.Version,
				})
			}

			mappedTool.LoadInstructions.Set(instr)
		}

	case A2SoftwareUcx:
		mappedTool.Backend = ToolBackendUcx
		if y.Software.Ucx == nil {
			err = util.MergeHttpErr(err, util.HttpErr(
				http.StatusBadRequest,
				"missing ucx information in 'software'",
			))
		} else {
			util.ValidateString(&y.Software.Ucx.Image, "software.image", 0, &err)

			mappedTool.Container = y.Software.Ucx.Image
			mappedTool.Image = y.Software.Ucx.Image
		}
	}

	for _, paramName := range y.ParametersOrder {
		param := y.Parameters[paramName]
		util.ValidateString(&paramName, fmt.Sprintf("parameters.%s", paramName), 0, &err)
		mapped := ApplicationParameter{
			Name: paramName,
		}

		var base A2ParamBase

		if param.File != nil {
			base = param.File.A2ParamBase
			mapped.Type = ApplicationParameterTypeInputFile
		} else if param.Directory != nil {
			base = param.Directory.A2ParamBase
			mapped.Type = ApplicationParameterTypeInputDirectory
		} else if param.License != nil {
			base = param.License.A2ParamBase
			mapped.Type = ApplicationParameterTypeLicenseServer
		} else if param.Job != nil {
			base = param.Job.A2ParamBase
			mapped.Type = ApplicationParameterTypePeer
		} else if param.PublicIP != nil {
			base = param.PublicIP.A2ParamBase
			mapped.Type = ApplicationParameterTypeNetworkIp
		} else if param.Integer != nil {
			base = param.Integer.A2ParamBase
			mapped.Type = ApplicationParameterTypeInteger

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
			if i.DefaultValue.Present && i.Max.Present && i.DefaultValue.Value > i.Max.Value {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s default value must not exceed the maximum value",
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
			if i.Min.Present {
				mapped.MinValue = i.Min.Value
			}
			if i.Max.Present {
				mapped.MaxValue = i.Max.Value
			}
			if i.Step.Present {
				mapped.Step = i.Step.Value
			}
		} else if param.FloatingPoint != nil {
			base = param.FloatingPoint.A2ParamBase
			mapped.Type = ApplicationParameterTypeFloatingPoint

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
			if f.DefaultValue.Present && f.Max.Present && f.DefaultValue.Value > f.Max.Value {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"%s default value must not exceed the maximum value",
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
			if f.Min.Present {
				mapped.MinValue = f.Min.Value
			}
			if f.Max.Present {
				mapped.MaxValue = f.Max.Value
			}
			if f.Step.Present {
				mapped.Step = f.Step.Value
			}
		} else if param.Boolean != nil {
			base = param.Boolean.A2ParamBase
			mapped.Type = ApplicationParameterTypeBoolean
			mapped.TrueValue = "true"
			mapped.FalseValue = "false"

			if param.Boolean.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(param.Boolean.DefaultValue.Value)
			}
		} else if param.Text != nil {
			base = param.Text.A2ParamBase
			mapped.Type = ApplicationParameterTypeText

			if param.Text.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(param.Text.DefaultValue.Value)
			}
		} else if param.TextArea != nil {
			base = param.TextArea.A2ParamBase
			mapped.Type = ApplicationParameterTypeTextArea

			if param.TextArea.DefaultValue.Present {
				mapped.DefaultValue = marshalJson(param.TextArea.DefaultValue.Value)
			}
		} else if param.Enumeration != nil {
			base = param.Enumeration.A2ParamBase
			mapped.Type = ApplicationParameterTypeEnumeration

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

				mapped.Options = append(mapped.Options, EnumOption{
					Name:  opt.Title,
					Value: opt.Value,
				})
			}
		} else if param.Workflow != nil {
			base = param.Workflow.A2ParamBase
			mapped.Type = ApplicationParameterTypeWorkflow
			workflow := param.Workflow
			hasWorkflowConfiguration := workflow.Init.Present || workflow.Job.Present || workflow.Readme.Present ||
				len(workflow.Parameters) != 0
			if hasWorkflowConfiguration {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"parameters.%s workflow configuration is not supported",
					paramName,
				))
			}
		}

		util.ValidateString(&base.Title, paramName, 0, &err)
		util.ValidateString(&base.Description, fmt.Sprintf("%s.description", paramName),
			util.StringValidationAllowMultiline|util.StringValidationAllowEmpty, &err)

		mapped.Title = base.Title
		mapped.Description = base.Description
		mapped.Optional = base.Optional

		mappedParameters = append(mappedParameters, mapped)
	}

	environmentNames := make([]string, 0, len(y.Environment))
	for name := range y.Environment {
		environmentNames = append(environmentNames, name)
	}
	slices.Sort(environmentNames)
	seenEnvironmentNames := map[string]bool{}
	for _, name := range environmentNames {
		value := y.Environment[name]
		util.ValidateString(&name, fmt.Sprintf("environment.%s (key)", name), 0, &err)
		util.ValidateString(&value, fmt.Sprintf("environment.%s (value)", name), 0, &err)
		if seenEnvironmentNames[name] {
			err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest, "duplicate environment key after trimming: %s", name))
		}
		seenEnvironmentNames[name] = true

		mappedEnvironment[name] = InvocationParameter{
			Type:                    InvocationParameterTypeWord,
			InvocationParameterWord: InvocationParameterWord{Word: value},
		}
	}

	sbatchNames := make([]string, 0, len(y.Sbatch))
	for name := range y.Sbatch {
		sbatchNames = append(sbatchNames, name)
	}
	slices.Sort(sbatchNames)
	seenSbatchNames := map[string]bool{}
	for _, name := range sbatchNames {
		value := y.Sbatch[name]
		util.ValidateString(&name, fmt.Sprintf("sbatch.%s (key)", name), 0, &err)
		util.ValidateString(&value, fmt.Sprintf("sbatch.%s (value)", name), 0, &err)
		if seenSbatchNames[name] {
			err = util.MergeHttpErr(err, util.HttpErr(http.StatusBadRequest, "duplicate sbatch key after trimming: %s", name))
		}
		seenSbatchNames[name] = true

		mappedSbatch[name] = InvocationParameter{
			Type:                    InvocationParameterTypeWord,
			InvocationParameterWord: InvocationParameterWord{Word: value},
		}
	}

	if y.Modules.Present {
		mods := y.Modules.Value
		mappedModules.Set(ModulesSection{
			MountPath: mods.MountPath,
			Optional:  mods.Optional,
		})
	}

	if y.Vnc.Present {
		if y.Vnc.Value.Enabled {
			mappedAppType = ApplicationTypeVnc
			p := y.Vnc.Value.Port
			if !p.Present {
				err = util.MergeHttpErr(err, util.HttpErr(
					http.StatusBadRequest,
					"vnc.port is required when VNC is enabled",
				))
			} else {
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
		if y.Web.Value.Enabled {
			mappedAppType = ApplicationTypeWeb

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
		} else {
			y.Web.Clear()
		}
	}

	if y.Ssh.Present {
		util.ValidateEnum(&y.Ssh.Value.Mode, A2SshModeOptions, "ssh.mode", &err)
	}
	if y.Inference.Present {
		util.ValidateEnum(&y.Inference.Value.Mode, A2InferenceModeOptions, "inference.mode", &err)
	}
	if validationErr := ValidateUcxExecutableMetadataSection(y.Ucx, "ucx.executable"); validationErr != nil {
		err = util.MergeHttpErr(err, validationErr)
	}

	if err != nil {
		return Application{}, err
	} else {
		return Application{
			WithAppMetadata: WithAppMetadata{
				Metadata: ApplicationMetadata{
					NameAndVersion: NameAndVersion{
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
			WithAppInvocation: WithAppInvocation{
				Invocation: ApplicationInvocationDescription{
					OutputFileGlobs: []string{"*"},
					ApplicationType: mappedAppType,
					Ucx:             y.Ucx,

					Tool: ToolReference{
						NameAndVersion: NameAndVersion{
							Name:    y.Name,
							Version: y.Version,
						},
						Tool: util.OptValue[Tool](Tool{
							Owner:       "UCloud",
							CreatedAt:   fndapi.Timestamp(time.Now()),
							ModifiedAt:  fndapi.Timestamp(time.Now()),
							Description: mappedTool,
						}),
					},

					Parameters: util.NonNilSlice(mappedParameters),

					Invocation: []InvocationParameter{
						{
							Type: InvocationParameterTypeJinja,
							InvocationParameterJinja: InvocationParameterJinja{
								Template: y.Invocation,
							},
						},
					},

					Vnc: util.OptMap(y.Vnc, func(value A2Vnc) VncDescription {
						return VncDescription{
							Password: value.Password.GetOrDefault(""),
							Port:     uint16(value.Port.Value),
						}
					}),

					Web: util.OptMap(y.Web, func(value A2Web) WebDescription {
						return WebDescription{Port: uint16(value.Port.GetOrDefault(80))}
					}),

					Ssh: util.OptMap(y.Ssh, func(value A2Ssh) SshDescription {
						res := SshDescription{}
						switch value.Mode {
						case A2SshModeDisabled:
							res.Mode = SshModeDisabled
						case A2SshModeOptional:
							res.Mode = SshModeOptional
						case A2SshModeMandatory:
							res.Mode = SshModeMandatory
						}
						return res
					}),

					Inference: util.OptMap(y.Inference, func(value A2Inference) InferenceDescription {
						res := InferenceDescription{}
						switch value.Mode {
						case A2InferenceModeNone:
							res.Mode = InferenceModeNone
						case A2InferenceModeOptional:
							res.Mode = InferenceModeOptional
						case A2InferenceModeMandatory:
							res.Mode = InferenceModeMandatory
						}
						return res
					}),

					Container: ContainerDescription{
						ChangeWorkingDirectory: true,
						RunAsRoot:              true,
						RunAsRealUser:          false,
					},

					AllowAdditionalPeers:  y.Features.Value.JobLinking,
					AllowAdditionalMounts: y.Features.Value.Folders,
					AllowMultiNode:        util.OptValue(y.Features.Value.MultiNode),
					AllowPublicIp:         y.Features.Value.IPAddresses,
					AllowPublicLink:       y.Features.Value.Links,
					JobAuditLogIsEnabled:  y.Features.Value.JobAuditLog,

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

func ValidateUcxExecutableMetadata(invocation *ApplicationInvocationDescription) *util.HttpError {
	return ValidateUcxExecutableMetadataSection(invocation.Ucx, "invocation.ucx.executable")
}

func ValidateUcxExecutableMetadataSection(ucx util.Option[UcxDescription], path string) *util.HttpError {
	if !ucx.Present || !ucx.Value.Executable.Present {
		return nil
	}

	executable := ucx.Value.Executable.Value
	if strings.TrimSpace(executable.ManifestUrl) == "" {
		return util.HttpErr(http.StatusBadRequest, "%s.manifestUrl is required", path)
	}
	if strings.HasPrefix(executable.ManifestUrl, "builtin://") {
		if _, ok := BuiltinUcxExecutableName(executable.ManifestUrl); !ok {
			return util.HttpErr(http.StatusBadRequest, "%s.manifestUrl must contain a valid built-in executable name", path)
		}
		return nil
	}
	if !strings.HasPrefix(executable.ManifestUrl, "https://") {
		return util.HttpErr(http.StatusBadRequest, "%s.manifestUrl must be an HTTPS URL", path)
	}
	if strings.TrimSpace(executable.PublicKey) == "" {
		return util.HttpErr(http.StatusBadRequest, "%s.publicKey is required", path)
	}
	if !strings.HasPrefix(executable.PublicKey, "ed25519:") {
		return util.HttpErr(http.StatusBadRequest, "%s.publicKey must use the ed25519: prefix", path)
	}
	if strings.TrimSpace(executable.BinaryName) == "" {
		return util.HttpErr(http.StatusBadRequest, "%s.binaryName is required", path)
	}

	return nil
}

func BuiltinUcxExecutableName(manifestUrl string) (string, bool) {
	const prefix = "builtin://"
	if !strings.HasPrefix(manifestUrl, prefix) {
		return "", false
	}

	name := strings.TrimPrefix(manifestUrl, prefix)
	if name == "" || name == "." || name == ".." {
		return "", false
	}
	for _, ch := range name {
		if (ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z') && (ch < '0' || ch > '9') && ch != '-' && ch != '_' && ch != '.' {
			return "", false
		}
	}
	return name, true
}
