package launcher

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

func EnvironmentIsReady() bool {
	_, err := fndapi.AuthBrowseIdentityProviders.Invoke(util.Empty{})
	return err == nil
}
