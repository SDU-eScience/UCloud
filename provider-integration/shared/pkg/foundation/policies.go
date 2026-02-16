package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Model
// =====================================================================================================================

type PolicySchema struct {
	Name          string           `json:"name"`
	Configuration []PolicyProperty `json:"configuration"`

	Title       string `json:"title"`
	Description string `json:"description"`
}

type PolicyProperty struct {
	Name string `json:"name"`
	Type string `json:"type"` // Enum, Text, Subnet, Integer, Float, Providers, Bool, TextList

	Title       string `json:"title"`
	Description string `json:"description"`

	Options []string `json:"options,omitempty"` // Enum, EnumSet
}

type PolicyPropertyType string

const (
	PolicyPropertyEnum      PolicyPropertyType = "Enum"
	PolicyPropertyText      PolicyPropertyType = "Text"
	PolicyPropertySubnet    PolicyPropertyType = "Subnet"
	PolicyPropertyInteger   PolicyPropertyType = "Integer"
	PolicyPropertyFloat     PolicyPropertyType = "Float"
	PolicyPropertyProviders PolicyPropertyType = "Providers"
	PolicyPropertyBool      PolicyPropertyType = "Bool"
	PolicyPropertyTextList  PolicyPropertyType = "TextList"
	PolicyPropertyEnumSet   PolicyPropertyType = "EnumSet"
)

type PolicySpecification struct {
	Schema     string                `json:"schema"`
	Project    rpc.ProjectId         `json:"project"`
	Properties []PolicyPropertyValue `json:"properties"`
}

type PolicyPropertyValue struct {
	Name string `json:"name"`

	Text string `json:"text,omitempty"` // Enum, Text, Subnet, Hostname

	Providers    []string `json:"providers,omitempty"`    // Providers
	Int          int      `json:"int,omitempty"`          // Int
	Float        float64  `json:"float,omitempty"`        // Float
	Bool         bool     `json:"bool,omitempty"`         // Bool
	TextElements []string `json:"textElements,omitempty"` // TextList, EnumSet
}

type Policy struct {
	Schema        PolicySchema        `json:"schema"`
	Specification PolicySpecification `json:"specification"`
}

// API
// =====================================================================================================================

const policiesBaseContext = "projects/v2/policies"

var PoliciesRetrieve = rpc.Call[util.Empty, map[string]Policy]{
	BaseContext: policiesBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

type PoliciesUpdateRequest struct {
	UpdatedPolicies map[string]PolicySpecification `json:"updatedPolicies"`
}

var PoliciesUpdate = rpc.Call[PoliciesUpdateRequest, util.Empty]{
	BaseContext: policiesBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}
