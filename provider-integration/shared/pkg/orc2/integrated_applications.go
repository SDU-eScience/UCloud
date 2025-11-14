package orchestrators

import (
	"fmt"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Implementers guide
// =====================================================================================================================
// A number of endpoints are required to implement an integrated application. Below is an easy to copy & paste
// definition. You will need to bring your own (easy to search & replace):
//
// - $Namespace$: The namespace under which your iapp will live (part of the url)
// - $ConfigType$: The type used for configuration
// - $Name$: The prefix to use in variable names for the iapp

/*
var $Name$RetrieveConfiguration = IAppRetrieveConfiguration[$ConfigType$]($Namespace$)
var $Name$UpdateConfiguration = IAppUpdateConfiguration[$ConfigType$]($Namespace$)
var $Name$Reset = IAppReset[$ConfigType$]($Namespace$)
var $Name$Restart = IAppRestart[$ConfigType$]($Namespace$)

var $Name$ProviderRetrieveConfiguration = IAppProviderRetrieveConfiguration[$ConfigType$]($Namespace$)
var $Name$ProviderUpdateConfiguration = IAppProviderUpdateConfiguration[$ConfigType$]($Namespace$)
var $Name$ProviderReset = IAppProviderReset[$ConfigType$]($Namespace$)
var $Name$ProviderRestart = IAppProviderRestart[$ConfigType$]($Namespace$)
*/

// Public API
// =====================================================================================================================

func IAppContext(namespace string) string {
	return fmt.Sprintf("iapps/%s", namespace)
}

type IAppRetrieveConfigRequest struct {
	Provider  string `json:"provider"`
	ProductId string `json:"productId"`
}

type IAppRetrieveConfigResponse[ConfigType any] struct {
	ETag   string     `json:"etag"`
	Config ConfigType `json:"config"`
}

func IAppRetrieveConfiguration[ConfigType any](namespace string) rpc.Call[IAppRetrieveConfigRequest, IAppRetrieveConfigResponse[ConfigType]] {
	return rpc.Call[IAppRetrieveConfigRequest, IAppRetrieveConfigResponse[ConfigType]]{
		BaseContext: IAppContext(namespace),
		Convention:  rpc.ConventionRetrieve,
		Roles:       rpc.RolesEndUser,
	}
}

type IAppUpdateConfigurationRequest[ConfigType any] struct {
	Provider     string              `json:"provider"`
	ProductId    string              `json:"productId"`
	Config       ConfigType          `json:"config"`
	ExpectedETag util.Option[string] `json:"expectedETag"`
}

func IAppUpdateConfiguration[ConfigType any](namespace string) rpc.Call[IAppUpdateConfigurationRequest[ConfigType], util.Empty] {
	return rpc.Call[IAppUpdateConfigurationRequest[ConfigType], util.Empty]{
		BaseContext: IAppContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesEndUser,
		Operation:   "update",
	}
}

type IAppResetRequest struct {
	ProductId    string              `json:"productId"`
	Principal    ResourceOwner       `json:"principal"`
	ExpectedETag util.Option[string] `json:"expectedETag"`
}

func IAppReset[ConfigType any](namespace string) rpc.Call[IAppRestartRequest, util.Empty] {
	return rpc.Call[IAppRestartRequest, util.Empty]{
		BaseContext: IAppContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesEndUser,
		Operation:   "reset",
	}
}

type IAppRestartRequest struct {
	Provider  string `json:"provider"`
	ProductId string `json:"productId"`
}

func IAppRestart[ConfigType any](namespace string) rpc.Call[IAppRestartRequest, util.Empty] {
	return rpc.Call[IAppRestartRequest, util.Empty]{
		BaseContext: IAppContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesEndUser,
		Operation:   "restart",
	}
}

// Provider API
// =====================================================================================================================

func IAppProviderContext(namespace string) string {
	return fmt.Sprintf("ucloud/%s/iapps/%s", rpc.ProviderPlaceholder, namespace)
}

type IAppProviderRetrieveConfigRequest struct {
	ProductId string        `json:"productId"`
	Principal ResourceOwner `json:"principal"`
}

type IAppProviderRetrieveConfigResponse[ConfigType any] = IAppRetrieveConfigResponse[ConfigType]

func IAppProviderRetrieveConfiguration[ConfigType any](namespace string) rpc.Call[IAppProviderRetrieveConfigRequest, IAppProviderRetrieveConfigResponse[ConfigType]] {
	return rpc.Call[IAppProviderRetrieveConfigRequest, IAppProviderRetrieveConfigResponse[ConfigType]]{
		BaseContext: IAppProviderContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesService,
		Operation:   "retrieve",
	}
}

type IAppProviderUpdateConfigurationRequest[ConfigType any] struct {
	ProductId    string              `json:"productId"`
	Principal    ResourceOwner       `json:"principal"`
	Config       ConfigType          `json:"config"`
	ExpectedETag util.Option[string] `json:"expectedETag"`
}

func IAppProviderUpdateConfiguration[ConfigType any](namespace string) rpc.Call[IAppProviderUpdateConfigurationRequest[ConfigType], util.Empty] {
	return rpc.Call[IAppProviderUpdateConfigurationRequest[ConfigType], util.Empty]{
		BaseContext: IAppProviderContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesService,
		Operation:   "update",
	}
}

type IAppProviderResetRequest struct {
	ProductId    string              `json:"productId"`
	Principal    ResourceOwner       `json:"principal"`
	ExpectedETag util.Option[string] `json:"expectedETag"`
}

func IAppProviderReset[ConfigType any](namespace string) rpc.Call[IAppProviderResetRequest, util.Empty] {
	return rpc.Call[IAppProviderResetRequest, util.Empty]{
		BaseContext: IAppProviderContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesService,
		Operation:   "reset",
	}
}

type IAppProviderRestartRequest struct {
	ProductId string        `json:"productId"`
	Principal ResourceOwner `json:"principal"`
}

func IAppProviderRestart[ConfigType any](namespace string) rpc.Call[IAppProviderRestartRequest, util.Empty] {
	return rpc.Call[IAppProviderRestartRequest, util.Empty]{
		BaseContext: IAppProviderContext(namespace),
		Convention:  rpc.ConventionUpdate,
		Roles:       rpc.RolesService,
		Operation:   "restart",
	}
}
