package orchestrators

import (
	"crypto/rand"
	"encoding/hex"
	"strings"
	"time"

	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type NetworkType string

const (
	NetworkTypeEthernet   NetworkType = "ethernet"
	NetworkTypeInfiniband NetworkType = "infiniband"
)

var NetworkTypeOptions = []NetworkType{
	NetworkTypeEthernet,
	NetworkTypeInfiniband,
}

type NetworkReclaimState string

const (
	NetworkReclaimActive  NetworkReclaimState = "active"
	NetworkReclaimPending NetworkReclaimState = "pending"
	NetworkReclaiming     NetworkReclaimState = "reclaiming"
)

var NetworkReclaimStateOptions = []NetworkReclaimState{
	NetworkReclaimActive,
	NetworkReclaimPending,
	NetworkReclaiming,
}

const PrivateNetworkInfinibandPrefixLength = 24

func NetworkTypeIsValid(networkType NetworkType) bool {
	_, ok := util.VerifyEnum(networkType, NetworkTypeOptions)
	return ok
}

func (s NetworkReclaimState) Effective() NetworkReclaimState {
	if s == "" {
		return NetworkReclaimActive
	}
	return s
}

type PrivateNetwork struct {
	Resource
	Specification PrivateNetworkSpecification `json:"specification"`
	Status        PrivateNetworkStatus        `json:"status"`
}

type PrivateNetworkSpecification struct {
	// Name must be a non-empty string with no new lines or invisible characters. It must not be longer than 256
	// characters.
	Name string `json:"name"`

	Type NetworkType `json:"type,omitempty"`

	// Cidr is an optional user-supplied IPv4 CIDR block for an Ethernet network. It must have a prefix length between
	// 16 and 24. If it is omitted, the provider allocates a block automatically. The CIDR is immutable after creation.
	// InfiniBand networks must not set it.
	Cidr util.Option[string] `json:"cidr"`

	ReclaimProtection bool `json:"reclaimProtection,omitempty"`

	ResourceSpecification
}

func (s PrivateNetworkSpecification) EffectiveType() NetworkType {
	if s.Type == "" {
		return NetworkTypeEthernet
	}
	return s.Type
}

func PrivateNetworkSubdomainFromName(name string) string {
	var builder strings.Builder
	previousHyphen := false
	for _, char := range strings.ToLower(name) {
		isAsciiLetter := char >= 'a' && char <= 'z'
		isDigit := char >= '0' && char <= '9'
		if isAsciiLetter || isDigit {
			builder.WriteRune(char)
			previousHyphen = false
		} else if builder.Len() > 0 && !previousHyphen {
			builder.WriteByte('-')
			previousHyphen = true
		}
	}

	subdomain := strings.Trim(builder.String(), "-")
	if subdomain == "" {
		subdomain = "network"
	}

	for _, prefix := range []string{"j", "ucloud", "vm", "im", "policy"} {
		if subdomain == prefix || strings.HasPrefix(subdomain, prefix+"-") {
			subdomain = "network-" + subdomain
			break
		}
	}

	if len(subdomain) > 63 {
		subdomain = strings.TrimRight(subdomain[:63], "-")
	}
	return subdomain
}

func PrivateNetworkSubdomainAddRandomSuffix(subdomain string) (string, error) {
	randomBytes := make([]byte, 5)
	_, err := rand.Read(randomBytes)
	if err != nil {
		return "", err
	}

	suffix := "-" + hex.EncodeToString(randomBytes)
	maxPrefixLength := 63 - len(suffix)
	if len(subdomain) > maxPrefixLength {
		subdomain = strings.TrimRight(subdomain[:maxPrefixLength], "-")
	}

	return subdomain + suffix, nil
}

type PrivateNetworkStatus struct {
	Subdomain string `json:"subdomain"`

	// Members correspond to all jobs currently participating in the network. Each element references a job by its ID.
	// Only jobs which are in a non-terminal state show up in this list. Thus jobs which are in SUCCESS, FAILURE or
	// EXPIRED will not show up. Jobs which are IN_QUEUE, SUSPENDED or RUNNING will appear.
	Members []string `json:"members"`

	// CidrBlock is the effective CIDR block of the network. It is set by the provider once the network has been
	// provisioned.
	CidrBlock util.Option[string] `json:"cidrBlock"`

	LastUsedAt   fnd.Timestamp              `json:"lastUsedAt"`
	ReclaimState NetworkReclaimState        `json:"reclaimState"`
	ReclaimAfter util.Option[fnd.Timestamp] `json:"reclaimAfter,omitempty"`

	ResourceStatus[PrivateNetworkSupport]
}

type PrivateNetworkSupport struct {
	Product          apm.ProductReference `json:"product"`
	NetworkType      NetworkType          `json:"networkType"`
	AddressPools     []string             `json:"addressPools,omitempty"`
	ExcludedRanges   []string             `json:"excludedRanges,omitempty"`
	MinPrefixLength  int                  `json:"minPrefixLength,omitempty"`
	MaxPrefixLength  int                  `json:"maxPrefixLength,omitempty"`
	RdmaResourceName string               `json:"rdmaResourceName,omitempty"`
	ReclaimPolicy    ReclaimPolicy        `json:"reclaimPolicy,omitempty"`
}

type ReclaimPolicy struct {
	Enabled              bool          `json:"enabled"`
	LowWatermarkPercent  int           `json:"lowWatermarkPercent"`
	HighWatermarkPercent int           `json:"highWatermarkPercent"`
	GracePeriod          time.Duration `json:"gracePeriod"`
	MinAge               time.Duration `json:"minAge"`
	MaxPerRun            int           `json:"maxPerRun"`
}

func (s PrivateNetworkSupport) EffectiveType() NetworkType {
	if s.NetworkType == "" {
		return NetworkTypeEthernet
	}
	return s.NetworkType
}

type PrivateNetworkFlags struct {
	ResourceFlags
}

const networksContext = "networks"

const privateNetworksContext = "private-networks"

// Private Network API
// =====================================================================================================================

var NetworksCreate = rpc.Call[fnd.BulkRequest[PrivateNetworkSpecification], fnd.BulkResponse[PrivateNetwork]]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionCreate,
	Roles:         rpc.RolesEndUser,
}

var NetworksDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], util.Empty]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionDelete,
	Roles:         rpc.RolesEndUser,
}

type PrivateNetworksSearchRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`
	Query        string              `json:"query"`

	PrivateNetworkFlags
}

var NetworksSearch = rpc.Call[PrivateNetworksSearchRequest, fnd.PageV2[PrivateNetwork]]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionSearch,
	Roles:         rpc.RolesEndUser,
}

type PrivateNetworksBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PrivateNetworkFlags
}

var NetworksBrowse = rpc.Call[PrivateNetworksBrowseRequest, fnd.PageV2[PrivateNetwork]]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionBrowse,
	Roles:         rpc.RolesEndUser,
}

type PrivateNetworksRetrieveRequest struct {
	Id string
	PrivateNetworkFlags
}

var NetworksRetrieve = rpc.Call[PrivateNetworksRetrieveRequest, PrivateNetwork]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionRetrieve,
	Roles:         rpc.RolesEndUser,
}

var NetworksUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesEndUser,
	Operation:     "updateAcl",
}

type PrivateNetworksUpdateLabelsRequest struct {
	Id     string            `json:"id"`
	Labels map[string]string `json:"labels"`
}

var NetworksUpdateLabels = rpc.Call[fnd.BulkRequest[PrivateNetworksUpdateLabelsRequest], util.Empty]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesEndUser,
	Operation:     "updateLabels",
}

var NetworksRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[PrivateNetworkSupport]]{
	BaseContext:   networksContext,
	ServerAliases: []string{privateNetworksContext},
	Convention:    rpc.ConventionRetrieve,
	Roles:         rpc.RolesEndUser,
	Operation:     "products",
}

// Private Network Control API
// =====================================================================================================================

const networksControlNamespace = "networks/control"

const privateNetworksControlNamespace = "private-networks/control"

func withServerAliases[Req any, Resp any](call rpc.Call[Req, Resp], aliases ...string) rpc.Call[Req, Resp] {
	call.ServerAliases = aliases
	return call
}

type PrivateNetworkUpdate struct {
	CidrBlock util.Option[string] `json:"cidrBlock"`
	Subdomain util.Option[string] `json:"subdomain"`
	Timestamp fnd.Timestamp       `json:"timestamp"`
}

type PrivateNetworksControlRetrieveRequest struct {
	JobId string `json:"jobId,omitempty"`
	Id    string `json:"id"`
	PrivateNetworkFlags
}

var NetworksControlRetrieve = rpc.Call[PrivateNetworksControlRetrieveRequest, PrivateNetwork]{
	BaseContext:   networksControlNamespace,
	ServerAliases: []string{privateNetworksControlNamespace},
	Convention:    rpc.ConventionRetrieve,
	Roles:         rpc.RolesProvider,
}

type PrivateNetworksControlBrowseRequest struct {
	JobId        string              `json:"jobId,omitempty"`
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	PrivateNetworkFlags
}

var NetworksControlBrowse = rpc.Call[PrivateNetworksControlBrowseRequest, fnd.PageV2[PrivateNetwork]]{
	BaseContext:   networksControlNamespace,
	ServerAliases: []string{privateNetworksControlNamespace},
	Convention:    rpc.ConventionBrowse,
	Roles:         rpc.RolesProvider,
}

var NetworksControlCreate = withServerAliases(
	ControlCreateCall[PrivateNetworkSpecification, fnd.BulkResponse[fnd.FindByStringId]](networksControlNamespace),
	privateNetworksControlNamespace,
)

var NetworksControlRegister = rpc.Call[fnd.BulkRequest[ProviderRegisteredResource[PrivateNetworkSpecification]], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext:   networksControlNamespace,
	ServerAliases: []string{privateNetworksControlNamespace},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesProvider,
	Operation:     "register",
}

var NetworksControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[PrivateNetworkUpdate]], util.Empty]{
	BaseContext:   networksControlNamespace,
	ServerAliases: []string{privateNetworksControlNamespace},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesProvider,
	Operation:     "update",
}

var NetworksControlUpdateLabels = rpc.Call[ControlMutateRequest[PrivateNetworksUpdateLabelsRequest], util.Empty]{
	BaseContext:   networksControlNamespace,
	ServerAliases: []string{privateNetworksControlNamespace},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesProvider,
	Operation:     "updateLabels",
}

var NetworksControlDelete = withServerAliases(
	ControlMutateCall[fnd.FindByStringId, fnd.BulkResponse[util.Empty]](networksControlNamespace, "delete"),
	privateNetworksControlNamespace,
)

// Private Network Provider API
// =====================================================================================================================

const networksProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/networks"

const privateNetworksProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/private-networks"

var NetworksProviderCreate = rpc.Call[fnd.BulkRequest[PrivateNetwork], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext:   networksProviderNamespace,
	ServerAliases: []string{privateNetworksProviderNamespace},
	Convention:    rpc.ConventionCreate,
	Roles:         rpc.RolesPrivileged,
}

var NetworksProviderDelete = rpc.Call[fnd.BulkRequest[PrivateNetwork], fnd.BulkResponse[util.Empty]]{
	BaseContext:   networksProviderNamespace,
	ServerAliases: []string{privateNetworksProviderNamespace},
	Convention:    rpc.ConventionDelete,
	Roles:         rpc.RolesPrivileged,
}

var NetworksProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[PrivateNetworkSupport]]{
	BaseContext:   networksProviderNamespace,
	ServerAliases: []string{privateNetworksProviderNamespace},
	Convention:    rpc.ConventionRetrieve,
	Roles:         rpc.RolesPrivileged,
	Operation:     "products",
}

var NetworksProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[PrivateNetwork]], fnd.BulkResponse[util.Empty]]{
	BaseContext:   networksProviderNamespace,
	ServerAliases: []string{privateNetworksProviderNamespace},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesPrivileged,
	Operation:     "updateAcl",
}

var NetworksProviderOnUpdatedLabels = rpc.Call[fnd.BulkRequest[PrivateNetwork], util.Empty]{
	BaseContext:   networksProviderNamespace,
	ServerAliases: []string{privateNetworksProviderNamespace},
	Convention:    rpc.ConventionUpdate,
	Roles:         rpc.RolesPrivileged,
	Operation:     "onUpdatedLabels",
}

var (
	PrivateNetworksCreate           = NetworksCreate
	PrivateNetworksDelete           = NetworksDelete
	PrivateNetworksSearch           = NetworksSearch
	PrivateNetworksBrowse           = NetworksBrowse
	PrivateNetworksRetrieve         = NetworksRetrieve
	PrivateNetworksUpdateAcl        = NetworksUpdateAcl
	PrivateNetworksUpdateLabels     = NetworksUpdateLabels
	PrivateNetworksRetrieveProducts = NetworksRetrieveProducts

	PrivateNetworksControlRetrieve     = NetworksControlRetrieve
	PrivateNetworksControlBrowse       = NetworksControlBrowse
	PrivateNetworksControlCreate       = NetworksControlCreate
	PrivateNetworksControlRegister     = NetworksControlRegister
	PrivateNetworksControlAddUpdate    = NetworksControlAddUpdate
	PrivateNetworksControlUpdateLabels = NetworksControlUpdateLabels
	PrivateNetworksControlDelete       = NetworksControlDelete

	PrivateNetworksProviderCreate           = NetworksProviderCreate
	PrivateNetworksProviderDelete           = NetworksProviderDelete
	PrivateNetworksProviderRetrieveProducts = NetworksProviderRetrieveProducts
	PrivateNetworksProviderUpdateAcl        = NetworksProviderUpdateAcl
	PrivateNetworksProviderOnUpdatedLabels  = NetworksProviderOnUpdatedLabels
)
