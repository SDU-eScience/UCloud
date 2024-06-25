package orchestrators

import (
	"encoding/json"
	fnd "ucloud.dk/pkg/foundation"
)

type NameAndVersion struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

type SimpleDuration struct {
	Hours   int
	Minutes int
	Seconds int
}

func (d *SimpleDuration) toMillis() int64 {
	return (int64(d.Hours)*60*60*1000 + int64(d.Minutes)*60*1000 + int64(d.Seconds)*1000)
}

func (d *SimpleDuration) fromMillis(durationMs int64) SimpleDuration {
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

type ToolBackend string

const (
	ToolBackendSingularity    ToolBackend = "SINGULARITY"
	ToolBackendDocker         ToolBackend = "DOCKER"
	ToolBackendVirtualMachine ToolBackend = "VIRTUAL_MACHINE"
	ToolBackendNative         ToolBackend = "NATIVE"
)

type ToolDescription struct {
	Info                  NameAndVersion `json:"info"`
	DefaultNumberOfNodes  int            `json:"defaultNumberOfNodes"`
	DefaultTimeAllocation SimpleDuration `json:"defaultTimeAllocation"`
	RequiredModules       []string       `json:"requiredModules"`
	Authors               []string       `json:"authors"`
	Title                 string         `json:"title"`
	Description           string         `json:"description"`
	Backend               ToolBackend    `json:"backend"`
	License               string         `json:"license"`
	Image                 string         `json:"image,omitempty"`
	SupportedProviders    []string       `json:"supportedProviders,omitempty"`
}

type Tool struct {
	Owner       string          `json:"owner"`
	CreatedAt   fnd.Timestamp   `json:"createdAt"`
	Description ToolDescription `json:"description"`
}

type ToolReference struct {
	NameAndVersion
	Tool Tool `json:"tool,omitempty"`
}

type Application struct {
	WithAppMetadata
	WithAppInvocation
}

type WithAppMetadata struct {
	Metadata ApplicationMetadata `json:"metadata"`
}

type WithAppInvocation struct {
	Invocation ApplicationInvocationDescription `json:"invocation"`
}

type WithAppFavorite struct {
	Favorite bool `json:"favorite"`
}

type ApplicationSummaryWithFavorite struct {
	WithAppMetadata
	WithAppFavorite
	Tags []string `json:"tags"` // TODO DEPRECATED???
}

type ApplicationGroupMetadata struct {
	Id int `json:"id"`
}

type ColorReplacements struct {
	Light map[int]int `json:"light,omitempty"`
	Dark  map[int]int `json:"dark,omitempty"`
}

type ApplicationGroupSpecification struct {
	Title            string            `json:"title"`
	Description      string            `json:"description"`
	DefaultFlavor    string            `json:"defaultFlavor,omitempty"`
	Categories       []int             `json:"categories"`
	ColorReplacement ColorReplacements `json:"colorReplacement"`
	LogoHasText      bool              `json:"logoHasText"`
}

type ApplicationGroupStatus struct {
	Applications []ApplicationSummaryWithFavorite `json:"applications,omitempty"`
}

type ApplicationGroup struct {
	Metadata      ApplicationGroupMetadata      `json:"metadata"`
	Specification ApplicationGroupSpecification `json:"specification"`
	Status        ApplicationGroupStatus        `json:"status"`
}

type ApplicationSummary struct {
	WithAppMetadata
}

type ApplicationMetadata struct {
	NameAndVersion
	Authors     []string         `json:"authors"`
	Title       string           `json:"title"`
	Description string           `json:"description"`
	Website     string           `json:"website,omitempty"`
	Public      bool             `json:"public"`
	FlavorName  string           `json:"flavorName,omitempty"`
	Group       ApplicationGroup `json:"group,omitempty"`
	CreatedAt   fnd.Timestamp    `json:"createdAt"`
}

type ApplicationInvocationDescription struct {
	Tool                  ToolReference
	Invocation            []InvocationParameter
	Parameters            []ApplicationParameter
	OutputFileGlobs       []string
	ApplicationType       ApplicationType
	Vnc                   VncDescription
	Web                   WebDescription
	Ssh                   SshDescription
	Container             ContainerDescription
	Environment           map[string]InvocationParameter
	AllowAdditionalMounts bool
	AllowAdditionalPeers  bool
	AllowMultiNode        bool
	AllowPublicIp         bool
	AllowPublicLink       bool
	FileExtensions        []string
	LicenseServers        []string
	Modules               ModulesSection
}

type VncDescription struct {
	Password string `json:"password,omitempty"`
	Port     uint16 `json:"port"`
}

type WebDescription struct {
	Port uint16 `json:"port"`
}

type SshMode string

const (
	SshModeDisabled  SshMode = "DISABLED"
	SshModeOptional  SshMode = "OPTIONAL"
	SshModeMandatory SshMode = "MANDATORY"
)

type SshDescription struct {
	Mode SshMode `json:"mode"`
}

type ContainerDescription struct {
	ChangeWorkingDirectory bool `json:"changeWorkingDirectory"`
	RunAsRoot              bool `json:"runAsRoot"`
	RunAsRealUser          bool `json:"runAsRealUser"`
}

type InvocationParameterType string

const (
	InvocationParameterTypeEnv      InvocationParameterType = "env"
	InvocationParameterTypeWord     InvocationParameterType = "word"
	InvocationParameterTypeVar      InvocationParameterType = "var"
	InvocationParameterTypeBoolFlag InvocationParameterType = "bool_flag"
)

type InvocationParameter struct {
	Type InvocationParameterType `json:"type"`
	InvocationParameterEnv
	InvocationParameterWord
	InvocationParameterVar
	InvocationParameterBoolFlag
}

type InvocationParameterEnv struct {
	Variable string `json:"variable,omitempty"`
}

type InvocationParameterWord struct {
	Word string `json:"word,omitempty"`
}

type InvocationParameterVar struct {
	VariableNames             []string `json:"variableNames,omitempty"`
	PrefixGlobal              string   `json:"prefixGlobal,omitempty"`
	SuffixGlobal              string   `json:"suffixGlobal,omitempty"`
	PrefixVariable            string   `json:"prefixVariable,omitempty"`
	SuffixVariable            string   `json:"suffixVariable,omitempty"`
	IsPrefixVariablePartOfArg bool     `json:"isPrefixVariablePartOfArg,omitempty"`
	IsSuffixVariablePartOfArg bool     `json:"isSuffixVariablePartOfArg,omitempty"`
}

type InvocationParameterBoolFlag struct {
	VariableName string `json:"variableName,omitempty"`
	Flag         string `json:"flag,omitempty"`
}

type ModulesSection struct {
	MountPath string   `json:"mountPath"`
	Optional  []string `json:"optional"`
}

// Application Parameters

type ApplicationParameter struct {
	Type         ApplicationParameterType `json:"type"`
	Name         string                   `json:"name"`
	Optional     bool                     `json:"optional"`
	DefaultValue json.RawMessage          `json:"defaultValue,omitempty"`
	Title        string                   `json:"title"`
	Description  string                   `json:"description"`
	MinValue     any                      `json:"min"`
	MaxValue     any                      `json:"max"`
	Step         any                      `json:"step"`
	UnitName     string                   `json:"unitName"`
	TrueValue    string                   `json:"trueValue"`
	FalseValue   string                   `json:"falseValue"`
	Options      []EnumOption             `json:"options"`
	Tagged       []string                 `json:"tagged"`
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
	Name  string
	Value string
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

// AppParameterValue

type AppParameterValue struct {
	Type     AppParameterValueType
	Path     string
	ReadOnly bool
	Value    any
	Hostname string
	JobId    string
	Id       string
}

type AppParameterValueType string

const (
	AppParameterValueTypeFile          AppParameterValueType = "file"
	AppParameterValueTypeBoolean       AppParameterValueType = "boolean"
	AppParameterValueTypeTextArea      AppParameterValueType = "textarea"
	AppParameterValueTypeText          AppParameterValueType = "text"
	AppParameterValueTypeInteger       AppParameterValueType = "integer"
	AppParameterValueTypeFloatingPoint AppParameterValueType = "floating_point"
	AppParameterValueTypePeer          AppParameterValueType = "peer"
	AppParameterValueTypeLicense       AppParameterValueType = "license_server"
	AppParameterValueTypeBlockStorage  AppParameterValueType = "block_storage"
	AppParameterValueTypeEnumeration   AppParameterValueType = "enumeration"
	AppParameterValueTypeNetwork       AppParameterValueType = "network"
	AppParameterValueTypeIngress       AppParameterValueType = "ingress"
)

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

func AppParameterValueTextArea(value string) AppParameterValue {
	return AppParameterValue{
		Type:  AppParameterValueTypeTextArea,
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
