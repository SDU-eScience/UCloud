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
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func Init() {
	controller.InitContainerRepositoryDatabase()
	controller.ContainerRepositories = controller.ContainerRepositoryService{
		Create: func(repository *orc.ContainerRepository) *util.HttpError { return nil },
		Delete: func(repository *orc.ContainerRepository) *util.HttpError { return nil },
	}

	if err := registerAuthentication(); err != nil {
		panic(err)
	}
	if err := ocidmiddlewarerepo.Register(accountingMiddlewareName, newAccountingRepository); err != nil {
		panic(err)
	}

	// Distribution uses its package-global logrus logger even when embedded as a handler.
	logrus.SetLevel(logrus.PanicLevel)

	registryHost := shared.ServiceConfig.Registry.Host

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
			Host:   "https://" + registryHost,
			Secret: shared.ServiceConfig.Registry.Secrets.RegistrySharedSecret,
		},
		Notifications: ocidconfig.Notifications{Endpoints: nil},
		Redis:         ocidconfig.Redis{},
		Health:        ocidconfig.Health{},
	}

	app := ocidhandlers.NewApp(context.Background(), &config)
	controller.Mux.HandleFunc(registryHost+"/auth/token", handleAuthenticationToken)
	controller.Mux.Handle(registryHost+"/", auditingMiddleware(app))
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: registryHost,
			Type:         gateway.RouteTypeIngress,
		},
	})
}

const RegistriesDirectory = "registries"
