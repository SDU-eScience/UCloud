package shared

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
)

func LoginDirectlyIntoDev() {
	cfg := InitializeUCloudClient()
	login, httpErr := fndapi.AuthPasswordLoginServer.Invoke(fndapi.PasswordLoginRequest{
		Username: "user",
		Password: "mypassword",
		Service:  "ucloud-cli",
	})
	if httpErr.AsError() != nil {
		panic(httpErr.Why)
	}
	cfg.TokenRef = login.RefreshToken
	_, err := SaveConfig(cfg)
	if err != nil {
		panic(err)
	}

	//fndapi.PasswordLoginRequest{}
}
