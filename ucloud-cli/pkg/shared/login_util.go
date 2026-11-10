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
	env := cfg.Environments[cfg.DefaultEnvironment]
	env.TokenRef = login.RefreshToken
	cfg.Username = login.Username
	cfg.Environments[cfg.DefaultEnvironment] = env
	_, err := SaveConfig(cfg)
	if err != nil {
		panic(err)
	}
}
