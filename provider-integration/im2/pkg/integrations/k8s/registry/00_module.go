package registry

import (
	"context"
	"path/filepath"

	ocidconfig "github.com/distribution/distribution/v3/configuration"
	ocidhandlers "github.com/distribution/distribution/v3/registry/handlers"
	ocidmiddlewarerepo "github.com/distribution/distribution/v3/registry/middleware/repository"
	_ "github.com/distribution/distribution/v3/registry/storage/driver/filesystem"
	"github.com/sirupsen/logrus"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/gateway"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/util"
)

func Init() {
	if err := registerAuthentication(); err != nil {
		panic(err)
	}
	if err := ocidmiddlewarerepo.Register(accountingMiddlewareName, newAccountingRepository); err != nil {
		panic(err)
	}

	// Distribution uses its package-global logrus logger even when embedded as a handler.
	logrus.SetLevel(logrus.PanicLevel)

	config := ocidconfig.Configuration{
		Version: ocidconfig.CurrentVersion,
		Log: ocidconfig.Log{
			Level:     ocidconfig.Loglevel("panic"),
			AccessLog: ocidconfig.AccessLog{Disabled: true},
			Hooks:     nil,
		},
		Storage: ocidconfig.Storage{
			"filesystem": {
				"rootdirectory": filepath.Join(shared.ServiceConfig.FileSystem.MountPoint, RegistriesDirectory),
			},
			"delete": {
				"enabled": true,
			},
			"redirect": {
				"disable": true,
			},
		},
		Auth: ocidconfig.Auth{
			authenticationName: {},
		},
		Middleware: map[string][]ocidconfig.Middleware{
			"repository": {{Name: accountingMiddlewareName}},
		},
		HTTP: ocidconfig.HTTP{
			Host:   "https://" + VirtualHost,
			Secret: util.SecureToken(),
		},
		Notifications: ocidconfig.Notifications{Endpoints: nil},
		Redis:         ocidconfig.Redis{},
		Health:        ocidconfig.Health{},
	}

	app := ocidhandlers.NewApp(context.Background(), &config)
	controller.Mux.Handle(VirtualHost+"/", auditingMiddleware(app))
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: VirtualHost,
			Type:         gateway.RouteTypeIngress,
		},
	})
}

const RegistriesDirectory = "registries"
const VirtualHost = "registry.localhost.direct"
