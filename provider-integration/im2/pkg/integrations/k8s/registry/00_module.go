package registry

import (
	"context"
	"net/http"
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
	controller.InitContainerRepositoryDatabase()
	controller.ContainerRepositories = controller.ContainerRepositoryService{
		Create:       repositoryCreate,
		Delete:       repositoryDelete,
		BrowseImages: imagesBrowse,
		DeleteImage:  imagesDelete,
		OnDeleted:    repositoryOnDeleted,
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
			"maintenance": {
				"uploadpurging": map[any]any{
					"enabled":  true,
					"age":      "24h",
					"interval": "1h",
					"dryrun":   false,
				},
			},
		},
		Auth: ocidconfig.Auth{
			authenticationName: {},
		},
		Middleware: map[string][]ocidconfig.Middleware{
			"repository": {{Name: accountingMiddlewareName}},
		},
		HTTP: ocidconfig.HTTP{
			Secret:       shared.ServiceConfig.Registry.Secrets.RegistrySharedSecret,
			RelativeURLs: true,
		},
		Notifications: ocidconfig.Notifications{Endpoints: nil},
		Redis:         ocidconfig.Redis{},
		Health:        ocidconfig.Health{},
	}

	app := ocidhandlers.NewApp(context.Background(), &config)
	if err := initAccounting(filepath.Join(shared.ServiceConfig.FileSystem.MountPoint, RegistriesDirectory)); err != nil {
		panic(err)
	}
	controller.Mux.Handle(registryHost+"/auth/token", auditMiddleware(http.HandlerFunc(handleAuthenticationToken), true))
	controller.Mux.Handle(registryHost+"/", auditMiddleware(app, false))
	gateway.SendMessage(gateway.ConfigurationMessage{
		RouteUp: &gateway.EnvoyRoute{
			Cluster:      gateway.ServerClusterName,
			CustomDomain: registryHost,
			Type:         gateway.RouteTypeIngress,
		},
	})
}

func Server() string {
	if util.DevelopmentModeEnabled() {
		return "http://" + Service()
	}
	return "https://" + Service()
}

func Service() string {
	if util.DevelopmentModeEnabled() {
		return shared.ServiceConfig.Registry.Host + ":8889"
	}
	return shared.ServiceConfig.Registry.Host
}

const RegistriesDirectory = "registries"
