package foundation

import (
	"encoding/json"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const AuthProvidersContext = "auth/providers"

type AuthProviderClaimToken struct {
	ClaimToken string `json:"claimToken"`
}

type PublicKeyAndRefreshToken struct {
	ProviderId   string `json:"providerId"`
	PublicKey    string `json:"publicKey"`
	RefreshToken string `json:"refreshToken"`
}

var AuthProvidersRenew = rpc.Call[BulkRequest[FindByStringId], BulkResponse[PublicKeyAndRefreshToken]]{
	BaseContext: AuthProvidersContext,
	Operation:   "renew",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
}

type RefreshToken struct {
	RefreshToken string `json:"refreshToken"`
}

var AuthProvidersRefresh = rpc.Call[BulkRequest[RefreshToken], BulkResponse[AccessTokenAndCsrf]]{
	BaseContext: AuthProvidersContext,
	Operation:   "refresh",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Audit: rpc.AuditRules{
		Transformer: func(request any) json.RawMessage {
			return json.RawMessage("{}")
		},
	},
}

type FindByProviderId struct {
	ProviderId string `json:"providerId"`
}

var AuthProvidersRefreshAsOrchestrator = rpc.Call[BulkRequest[FindByProviderId], BulkResponse[AccessTokenAndCsrf]]{
	BaseContext: AuthProvidersContext,
	Operation:   "refreshAsOrchestrator",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
}

// NOTE(Dan): The generate key-pair function will not be needed after provider services are migrated

type PublicAndPrivateKey struct {
	PublicKey  string
	PrivateKey string
}

var AuthProvidersGenerateKeyPair = rpc.Call[util.Empty, PublicAndPrivateKey]{
	BaseContext: AuthProvidersContext,
	Operation:   "generateKeyPair",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
}

const ProviderSubjectPrefix = "#P_"
