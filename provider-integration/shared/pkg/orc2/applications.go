package orchestrators

import (
	"encoding/json"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

type NameAndVersion struct {
	Name    string `json:"name" yaml:"name"`
	Version string `json:"version" yaml:"version"`
}

type SimpleDuration struct {
	Hours   int `json:"hours" yaml:"hours"`
	Minutes int `json:"minutes" yaml:"minutes"`
	Seconds int `json:"seconds" yaml:"seconds"`
}

func (d SimpleDuration) ToMillis() int64 {
	return int64(d.Hours)*60*60*1000 + int64(d.Minutes)*60*1000 + int64(d.Seconds)*1000
}

func SimpleDurationFromMillis(durationMs int64) SimpleDuration {
	var hours = int(durationMs / (1000 * 60 * 60))
	var minutes = int(durationMs % (1000 * 60 * 60) / (1000 * 60))
	var seconds = int(((durationMs % (1000 * 60 * 60)) / (1000 * 60)) / 1000)

	return SimpleDuration{hours, minutes, seconds}
}

type ApplicationType string

const (
	ApplicationTypeBatch ApplicationType = "BATCH"
	ApplicationTypeVnc   ApplicationType = "VNC"
	ApplicationTypeWeb   ApplicationType = "WEB"
)

var ApplicationTypeOptions = []ApplicationType{
	ApplicationTypeBatch,
	ApplicationTypeVnc,
	ApplicationTypeWeb,
}

type ToolBackend string

const (
	ToolBackendSingularity    ToolBackend = "SINGULARITY"
	ToolBackendDocker         ToolBackend = "DOCKER"
	ToolBackendVirtualMachine ToolBackend = "VIRTUAL_MACHINE"
	ToolBackendNative         ToolBackend = "NATIVE"
)

type ToolDescription struct {
	Info                  NameAndVersion                    `json:"info"`
	DefaultNumberOfNodes  int                               `json:"defaultNumberOfNodes"`
	DefaultTimeAllocation SimpleDuration                    `json:"defaultTimeAllocation"`
	RequiredModules       []string                          `json:"requiredModules"`
	Authors               []string                          `json:"authors"`
	Title                 string                            `json:"title"`
	Description           string                            `json:"description"`
	Backend               ToolBackend                       `json:"backend"`
	License               string                            `json:"license"`
	Image                 string                            `json:"image"`
	Container             string                            `json:"container"`
	SupportedProviders    []string                          `json:"supportedProviders"`
	LoadInstructions      util.Option[ToolLoadInstructions] `json:"loadInstructions"`
}

type ToolLoadInstructionsType string

const (
	ToolLoadInstructionsNative ToolLoadInstructionsType = "Native"
)

type ToolLoadInstructions struct {
	Type         ToolLoadInstructionsType `json:"type" yaml:"type"`
	Applications []NativeApplication      `json:"applications" yaml:"applications"`
}

type NativeApplication struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

type Tool struct {
	Owner       string          `json:"owner" yaml:"owner"`
	CreatedAt   fnd.Timestamp   `json:"createdAt" yaml:"createdAt"`
	Description ToolDescription `json:"description" yaml:"description"`
}

type ToolReference struct {
	NameAndVersion
	Tool util.Option[Tool] `json:"tool" yaml:"tool"`
}

type Application struct {
	WithAppMetadata
	WithAppInvocation

	Favorite util.Option[bool] `json:"favorite" yaml:"favorite"`
	Versions []string          `json:"versions" yaml:"versions"`
}

type WithAppMetadata struct {
	Metadata ApplicationMetadata `json:"metadata" yaml:"metadata"`
}

type WithAppInvocation struct {
	Invocation ApplicationInvocationDescription `json:"invocation" yaml:"invocation"`
}

type WithAppFavorite struct {
	Favorite bool `json:"favorite" yaml:"favorite"`
}

type ApplicationSummaryWithFavorite struct {
	WithAppMetadata
	WithAppFavorite
	Tags []string `json:"tags" yaml:"tags"`
}

type ApplicationGroupMetadata struct {
	Id int `json:"id"`
}

type ColorReplacements struct {
	Light map[int]int `json:"light"`
	Dark  map[int]int `json:"dark"`
}

type ApplicationGroupSpecification struct {
	Title            string            `json:"title" yaml:"title"`
	Description      string            `json:"description" yaml:"description"`
	DefaultFlavor    string            `json:"defaultFlavor" yaml:"defaultFlavor"`
	Categories       []int             `json:"categories" yaml:"categories"`
	ColorReplacement ColorReplacements `json:"colorReplacement" yaml:"colorReplacement"`
	LogoHasText      bool              `json:"logoHasText" yaml:"logoHasText"`
}

type ApplicationGroupStatus struct {
	Applications []Application `json:"applications" yaml:"applications"`
}

type ApplicationGroup struct {
	Metadata      ApplicationGroupMetadata      `json:"metadata" yaml:"metadata"`
	Specification ApplicationGroupSpecification `json:"specification" yaml:"specification"`
	Status        ApplicationGroupStatus        `json:"status" yaml:"status"`
}

type ApplicationSummary struct {
	WithAppMetadata
}

type ApplicationMetadata struct {
	NameAndVersion
	Authors     []string            `json:"authors" yaml:"authors"`
	Title       string              `json:"title" yaml:"title"`
	Description string              `json:"description" yaml:"description"`
	Website     string              `json:"website" yaml:"website"`
	Public      bool                `json:"public" yaml:"public"`
	FlavorName  util.Option[string] `json:"flavorName" yaml:"flavorName"`
	Group       ApplicationGroup    `json:"group" yaml:"group"`
	CreatedAt   fnd.Timestamp       `json:"createdAt" yaml:"createdAt"`
}

type ApplicationInvocationDescription struct {
	Tool                  ToolReference                  `json:"tool" yaml:"tool"`
	Invocation            []InvocationParameter          `json:"invocation" yaml:"invocation"`
	Parameters            []ApplicationParameter         `json:"parameters" yaml:"parameters"`
	OutputFileGlobs       []string                       `json:"outputFileGlobs" yaml:"outputFileGlobs"`
	ApplicationType       ApplicationType                `json:"applicationType" yaml:"applicationType"`
	Vnc                   util.Option[VncDescription]    `json:"vnc" yaml:"vnc"`
	Web                   util.Option[WebDescription]    `json:"web" yaml:"web"`
	Ssh                   util.Option[SshDescription]    `json:"ssh" yaml:"ssh"`
	Container             ContainerDescription           `json:"container" yaml:"container"`
	Environment           map[string]InvocationParameter `json:"environment" yaml:"environment"`
	AllowAdditionalMounts util.Option[bool]              `json:"allowAdditionalMounts" yaml:"allowAdditionalMounts"`
	AllowAdditionalPeers  util.Option[bool]              `json:"allowAdditionalPeers" yaml:"allowAdditionalPeers"`
	AllowMultiNode        util.Option[bool]              `json:"allowMultiNode" yaml:"allowMultiNode"`
	AllowPublicIp         util.Option[bool]              `json:"allowPublicIp" yaml:"allowPublicIp"`
	AllowPublicLink       util.Option[bool]              `json:"allowPublicLink" yaml:"allowPublicLink"`
	FileExtensions        []string                       `json:"fileExtensions" yaml:"fileExtensions"`
	LicenseServers        []string                       `json:"licenseServers" yaml:"licenseServers"`
	Modules               ModulesSection                 `json:"modules" yaml:"modules"`
	Sbatch                map[string]InvocationParameter `json:"sbatch" yaml:"sbatch"`
}

type VncDescription struct {
	Password string `json:"password" yaml:"password"`
	Port     uint16 `json:"port" yaml:"port"`
}

type WebDescription struct {
	Port uint16 `json:"port" yaml:"port"`
}

type SshMode string

const (
	SshModeDisabled  SshMode = "DISABLED"
	SshModeOptional  SshMode = "OPTIONAL"
	SshModeMandatory SshMode = "MANDATORY"
)

var SshModeOptions = []SshMode{
	SshModeDisabled,
	SshModeOptional,
	SshModeMandatory,
}

type SshDescription struct {
	Mode SshMode `json:"mode" yaml:"mode"`
}

type ContainerDescription struct {
	ChangeWorkingDirectory bool `json:"changeWorkingDirectory" yaml:"changeWorkingDirectory"`
	RunAsRoot              bool `json:"runAsRoot" yaml:"runAsRoot"`
	RunAsRealUser          bool `json:"runAsRealUser" yaml:"runAsRealUser"`
}

type InvocationParameterType string

const (
	InvocationParameterTypeEnv      InvocationParameterType = "env"
	InvocationParameterTypeWord     InvocationParameterType = "word"
	InvocationParameterTypeVar      InvocationParameterType = "var"
	InvocationParameterTypeBoolFlag InvocationParameterType = "bool_flag"
	InvocationParameterTypeJinja    InvocationParameterType = "jinja"
)

type InvocationParameter struct {
	Type                        InvocationParameterType `json:"type" yaml:"type"`
	InvocationParameterEnv      `yaml:",inline"`
	InvocationParameterWord     `yaml:",inline"`
	InvocationParameterVar      `yaml:",inline"`
	InvocationParameterBoolFlag `yaml:",inline"`
	InvocationParameterJinja    `yaml:",inline"`
}

type InvocationParameterEnv struct {
	Variable string `json:"variable" yaml:"variable"`
}

type InvocationParameterWord struct {
	Word string `json:"word" yaml:"word"`
}

type InvocationParameterVar struct {
	VariableNames             []string `json:"variableNames" yaml:"VariableNames"`
	PrefixGlobal              string   `json:"prefixGlobal" yaml:"prefixGlobal"`
	SuffixGlobal              string   `json:"suffixGlobal" yaml:"suffixGlobal"`
	PrefixVariable            string   `json:"prefixVariable" yaml:"prefixVariable"`
	SuffixVariable            string   `json:"suffixVariable" yaml:"suffixVariable"`
	IsPrefixVariablePartOfArg bool     `json:"isPrefixVariablePartOfArg" yaml:"isPrefixVariablePartOfArg"`
	IsSuffixVariablePartOfArg bool     `json:"isSuffixVariablePartOfArg" yaml:"isSuffixVariablePartOfArg"`
}

type InvocationParameterBoolFlag struct {
	VariableName string `json:"variableName" yaml:"variableName"`
	Flag         string `json:"flag" yaml:"flag"`
}

type InvocationParameterJinja struct {
	Template string `json:"template" yaml:"template"`
}

type ModulesSection struct {
	MountPath string   `json:"mountPath" yaml:"mountPath"`
	Optional  []string `json:"optional" yaml:"optional"`
}

// Application Parameters

type ApplicationParameter struct {
	Type             ApplicationParameterType `json:"type" yaml:"type"`
	Name             string                   `json:"name" yaml:"name"`
	Optional         bool                     `json:"optional" yaml:"optional"`
	DefaultValue     json.RawMessage          `json:"defaultValue" yaml:"defaultValue"`
	Title            string                   `json:"title" yaml:"title"`
	Description      string                   `json:"description" yaml:"description"`
	MinValue         any                      `json:"min" yaml:"minValue"`
	MaxValue         any                      `json:"max" yaml:"maxValue"`
	Step             any                      `json:"step" yaml:"step"`
	UnitName         string                   `json:"unitName" yaml:"unitName"`
	TrueValue        string                   `json:"trueValue" yaml:"trueValue"`
	FalseValue       string                   `json:"falseValue" yaml:"falseValue"`
	Options          []EnumOption             `json:"options" yaml:"options"`
	Tagged           []string                 `json:"tagged" yaml:"tagged"`
	SupportedModules []Module                 `json:"supportedModules" yaml:"supportedModules"`
}

type Module struct {
	Name             string     `json:"name" yaml:"name"`
	Description      string     `json:"description" yaml:"description"`
	ShortDescription string     `json:"shortDescription" yaml:"shortDescription"`
	DependsOn        [][]string `json:"dependsOn" yaml:"dependsOn"`
	DocumentationUrl string     `json:"documentationUrl" yaml:"documentationUrl"`
}

type ApplicationParameterType string

const (
	ApplicationParameterTypeInputFile      ApplicationParameterType = "input_file"
	ApplicationParameterTypeInputDirectory ApplicationParameterType = "input_directory"
	ApplicationParameterTypeText           ApplicationParameterType = "text"
	ApplicationParameterTypeTextArea       ApplicationParameterType = "textarea"
	ApplicationParameterTypeInteger        ApplicationParameterType = "integer"
	ApplicationParameterTypeBoolean        ApplicationParameterType = "boolean"
	ApplicationParameterTypeEnumeration    ApplicationParameterType = "enumeration"
	ApplicationParameterTypeFloatingPoint  ApplicationParameterType = "floating_point"
	ApplicationParameterTypePeer           ApplicationParameterType = "peer"
	ApplicationParameterTypeLicenseServer  ApplicationParameterType = "license_server"
	ApplicationParameterTypeIngress        ApplicationParameterType = "ingress"
	ApplicationParameterTypeNetworkIp      ApplicationParameterType = "network_ip"
	ApplicationParameterTypeWorkflow       ApplicationParameterType = "workflow"
	ApplicationParameterTypeReadme         ApplicationParameterType = "readme"
	ApplicationParameterTypeModuleList     ApplicationParameterType = "modules"
)

func ApplicationParameterInputFile(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeInputFile,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterInputDirectory(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeInputDirectory,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterText(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeText,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterTextArea(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeTextArea,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterInteger(name string, optional bool, title string, description string, minValue int64, maxValue int64, step int64, unitName string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeInteger,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
		MinValue:    minValue,
		MaxValue:    maxValue,
		Step:        step,
		UnitName:    unitName,
	}
}

func ApplicationParameterFloatingPoint(name string, optional bool, title string, description string, minValue float64, maxValue float64, step float64, unitName string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeFloatingPoint,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
		MinValue:    minValue,
		MaxValue:    maxValue,
		Step:        step,
		UnitName:    unitName,
	}
}

func ApplicationParameterBoolean(name string, optional bool, title string, description string, trueValue string, falseValue string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeBoolean,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
		TrueValue:   trueValue,
		FalseValue:  falseValue,
	}
}

type EnumOption struct {
	Name  string `json:"name"`
	Value string `json:"value"`
}

func ApplicationParameterEnumeration(name string, optional bool, title string, description string, options []EnumOption) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeEnumeration,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
		Options:     options,
	}
}

func ApplicationParameterPeer(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeEnumeration,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterIngress(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeEnumeration,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterLicenseServer(name string, optional bool, title string, description string, tagged []string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeEnumeration,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
		Tagged:      tagged,
	}
}

func ApplicationParameterNetworkIp(name string, optional bool, title string, description string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeNetworkIp,
		Name:        name,
		Optional:    optional,
		Title:       title,
		Description: description,
	}
}

func ApplicationParameterModuleList(name string, title string, description string, modules []Module) ApplicationParameter {
	return ApplicationParameter{
		Type:             ApplicationParameterTypeModuleList,
		Name:             name,
		Optional:         false,
		DefaultValue:     json.RawMessage("[]"),
		Title:            title,
		Description:      description,
		SupportedModules: modules,
	}
}

func ApplicationParameterReadme(readme string) ApplicationParameter {
	return ApplicationParameter{
		Type:        ApplicationParameterTypeReadme,
		Name:        "__readme__",
		Optional:    true,
		Title:       "README",
		Description: readme,
	}
}

// AppParameterValue

type AppParameterValue struct {
	Type          AppParameterValueType `json:"type" yaml:"type"`
	Path          string                `json:"path" yaml:"path"`
	ReadOnly      bool                  `json:"readOnly" yaml:"readOnly"`
	Value         any                   `json:"value" yaml:"value"`
	Hostname      string                `json:"hostname" yaml:"hostname"`
	JobId         string                `json:"jobId" yaml:"jobId"`
	Id            string                `json:"id" yaml:"id"`
	Specification WorkflowSpecification `json:"specification" yaml:"specification"`
	Modules       []string              `json:"modules" yaml:"modules"`
}

type AppParameterValueType string

const (
	AppParameterValueTypeFile          AppParameterValueType = "file"
	AppParameterValueTypeBoolean       AppParameterValueType = "boolean"
	AppParameterValueTypeText          AppParameterValueType = "text"
	AppParameterValueTypeInteger       AppParameterValueType = "integer"
	AppParameterValueTypeFloatingPoint AppParameterValueType = "floating_point"
	AppParameterValueTypePeer          AppParameterValueType = "peer"
	AppParameterValueTypeLicense       AppParameterValueType = "license_server"
	AppParameterValueTypeBlockStorage  AppParameterValueType = "block_storage"
	AppParameterValueTypeNetwork       AppParameterValueType = "network"
	AppParameterValueTypeIngress       AppParameterValueType = "ingress"
	AppParameterValueTypeWorkflow      AppParameterValueType = "workflow"
	AppParameterValueTypeModuleList    AppParameterValueType = "modules"
)

func AppParameterValueModuleList(modules []string) AppParameterValue {
	return AppParameterValue{
		Type:    AppParameterValueTypeModuleList,
		Modules: modules,
	}
}

func AppParameterValueFile(path string, readOnly bool) AppParameterValue {
	return AppParameterValue{
		Type:     AppParameterValueTypeFile,
		Path:     path,
		ReadOnly: readOnly,
	}
}

func AppParameterValueBoolean(value bool) AppParameterValue {
	return AppParameterValue{
		Type:  AppParameterValueTypeBoolean,
		Value: value,
	}
}

func AppParameterValueText(value string) AppParameterValue {
	return AppParameterValue{
		Type:  AppParameterValueTypeText,
		Value: value,
	}
}

func AppParameterValueInteger(value int64) AppParameterValue {
	return AppParameterValue{
		Type:  AppParameterValueTypeInteger,
		Value: value,
	}
}

func AppParameterValueFloatingPoint(value float64) AppParameterValue {
	return AppParameterValue{
		Type:  AppParameterValueTypeFloatingPoint,
		Value: value,
	}
}

func AppParameterValuePeer(hostname string, jobId string) AppParameterValue {
	return AppParameterValue{
		Type:     AppParameterValueTypePeer,
		Hostname: hostname,
		JobId:    jobId,
	}
}

func AppParameterValueLicense(id string) AppParameterValue {
	return AppParameterValue{
		Type: AppParameterValueTypeLicense,
		Id:   id,
	}
}

func AppParameterValueBlockStorage(id string) AppParameterValue {
	return AppParameterValue{
		Type: AppParameterValueTypeBlockStorage,
		Id:   id,
	}
}

func AppParameterValueNetwork(id string) AppParameterValue {
	return AppParameterValue{
		Type: AppParameterValueTypeNetwork,
		Id:   id,
	}
}

func AppParameterValueIngress(id string) AppParameterValue {
	return AppParameterValue{
		Type: AppParameterValueTypeIngress,
		Id:   id,
	}
}

func InvocationWord(word string) InvocationParameter {
	result := InvocationParameter{}
	result.Type = InvocationParameterTypeWord
	result.Word = word
	return result
}

func InvocationVar(varName string) InvocationParameter {
	result := InvocationParameter{}
	result.Type = InvocationParameterTypeVar
	result.InvocationParameterVar = InvocationParameterVar{
		VariableNames: []string{varName},
	}
	return result
}
