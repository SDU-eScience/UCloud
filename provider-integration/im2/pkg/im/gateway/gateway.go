package gateway

import (
	"bytes"
	_ "embed"
	"encoding/base64"
	"encoding/gob"
	"fmt"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"sync/atomic"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
	"unicode"
)

const fileConfig = "config.yaml"
const fileBadGateway = "bad-gateway.html"

//go:embed bad-gateway.html
var badGatewayHtml []byte

const ServerClusterName = "_UCloud"
const ServerClusterPort = 42000

type ConfigurationMessage struct {
	ClusterUp              *EnvoyCluster
	ClusterDown            *EnvoyCluster
	RouteUp                *EnvoyRoute
	RouteDown              *EnvoyRoute
	LaunchingUserInstances *bool
}

type Config struct {
	ListenAddress   string
	Port            int
	InitialClusters []*EnvoyCluster
	InitialRoutes   []*EnvoyRoute
}

var configChannel chan []byte
var isLaunchingUserInstances = false

func Initialize(config Config, channel chan []byte) {
	Pause()

	var routes = make(map[*EnvoyRoute]bool)
	var clusters = make(map[string]*EnvoyCluster)

	configChannel = channel

	stateDir := cfg.Provider.Envoy.StateDirectory
	internalAddress := cfg.Provider.Envoy.InternalAddressToProvider
	managedExternally := cfg.Provider.Envoy.ManagedExternally
	executable := cfg.Provider.Envoy.Executable
	useFunceWrapper := cfg.Provider.Envoy.FunceWrapper

	{
		var err error
		err = os.WriteFile(
			fmt.Sprintf("%v/%v", stateDir, fileBadGateway),
			badGatewayHtml,
			0o600,
		)

		if err == nil {
			adminSection := ""
			if util.DevelopmentModeEnabled() {
				adminSection = adminSectionDev
			} else {
				adminSection = fmt.Sprintf(adminSectionProd, filepath.Join(stateDir, "admin.sock"))
			}

			err = os.WriteFile(
				fmt.Sprintf("%v/%v", stateDir, fileConfig),
				[]byte(
					fmt.Sprintf(
						envoyConfigTemplate,
						adminSection,
						filepath.Join(stateDir, "xds.sock"),
					)),
				0o600,
			)
		}

		if err != nil {
			log.Error("Failed to write required configuration files for the gateway: %v", err)
			os.Exit(1)
		}
	}

	if len(config.InitialClusters) > 0 || len(config.InitialRoutes) > 0 {
		for _, route := range config.InitialRoutes {
			routes[route] = true
		}

		for _, cluster := range config.InitialClusters {
			clusters[cluster.Name] = cluster
		}
	} else {
		routes[&EnvoyRoute{
			Type:       RouteTypeUser,
			Cluster:    ServerClusterName,
			Identifier: "",
		}] = true

		routes[&EnvoyRoute{
			Type:    RouteTypeAuthorize,
			Cluster: ServerClusterName,
		}] = true

		clusters[ServerClusterName] = &EnvoyCluster{
			Name:    ServerClusterName,
			Address: internalAddress,
			Port:    ServerClusterPort,
			UseDNS:  !unicode.IsDigit([]rune(internalAddress)[0]),
		}
	}

	go startConfigurationServer()

	go func() {
		for binMessage := range configChannel {
			message := ConfigurationMessage{}
			err := gob.NewDecoder(bytes.NewBuffer(binMessage)).Decode(&message)
			if err != nil {
				log.Warn("Failed to decode message: %", err)
				continue
			}

			if message.LaunchingUserInstances != nil {
				isLaunchingUserInstances = *message.LaunchingUserInstances
			}

			if message.RouteDown != nil {
				delete(routes, message.RouteDown)
			}

			if message.ClusterDown != nil {
				delete(clusters, message.ClusterDown.Name)

				var routesToDelete []*EnvoyRoute = nil
				for route, _ := range routes {
					if route.Cluster == message.ClusterDown.Name {
						routesToDelete = append(routesToDelete, route)
					}
				}

				for _, toDelete := range routesToDelete {
					delete(routes, toDelete)
				}
			}

			if message.RouteUp != nil {
				routes[message.RouteUp] = true
			}

			if message.ClusterUp != nil {
				clusters[message.ClusterUp.Name] = message.ClusterUp
			}

			// NOTE(Dan): We configure even if no changes are made. This allows to resume the system and
			// resynchronizing by simply sending an empty configuration message.

			if !paused.Load() {
				sortedRoutes := sortRoutes(routes)
				snapshot := createConfigurationSnapshot(config.ListenAddress, config.Port, sortedRoutes, clusters)
				setActiveSnapshot(snapshot)
			}
		}
	}()

	if !managedExternally {
		go func() {
			logFilePath := filepath.Join(cfg.Provider.Logs.Directory, "envoy.log")

			for {
				func() {
					logFile, err := os.OpenFile(logFilePath, os.O_RDWR|os.O_CREATE, 0666)
					if err != nil {
						log.Error("Failed to create log file for gateway: %v", err)
						os.Exit(1)
					}

					defer util.SilentClose(logFile)

					var args []string = nil
					if useFunceWrapper {
						args = append(args, "run")
					}
					args = append(args, "--config-path")
					args = append(args, fmt.Sprintf("%v/%v", stateDir, fileConfig))

					cmd := exec.Command(executable, args...)

					// NOTE(Dan, 09/08/2024): Most of the systems this will run on runs older versions of glibc
					// (and gcc). The newest version of Envoy this can run is 1.23.12 but the newest version in
					// func-e is 1.23.4.
					cmd.Env = append(cmd.Env, "ENVOY_VERSION=1.23.4")
					cmd.Stdout = logFile
					cmd.Stderr = logFile

					err = cmd.Start()
					if err != nil {
						log.Warn("Failed to start gateway: %v", err)
						return
					}

					err = cmd.Wait()
					if err != nil {
						log.Warn("Gateway has crashed: %v (see %v for details)", err, logFilePath)
						time.Sleep(5 * time.Minute)
					} else {
						log.Warn("Gateway has ended early, see %v for details", logFilePath)
					}
				}()
			}
		}()
	}
}

var paused = atomic.Bool{}

func Resume() {
	paused.Store(false)
	SendMessage(ConfigurationMessage{})
}

func SendMessage(message ConfigurationMessage) {
	var buf bytes.Buffer
	err := gob.NewEncoder(&buf).Encode(message)
	if err != nil {
		log.Warn("Failed to encode message %v: %v", message, err)
		return
	}
	configChannel <- buf.Bytes()
}

func Pause() {
	paused.Store(true)
}

func b64(value string) string {
	data := base64.StdEncoding.EncodeToString([]byte(value))
	return data
}

func urlEncode(value string) string {
	return url.QueryEscape(value)
}

// NOTE(Dan): This assumes that the configuration can be trusted, which doesn't seem like an unreasonable assumption
// given that it must be owned by the service user on the file-system (and that this is verified).
// NOTE(Dan): xDS server must run on a unix domain socket if other users can access the system
// NOTE(Dan): Admin server must only be accessible via socket/dev mode only

const adminSectionDev = `
admin:
  access_log_path: /dev/null
  address:
    socket_address:
      address: 0.0.0.0
      port_value: 41493
`

const adminSectionProd = `
admin:
  access_log_path: /dev/null
  address:
    pipe:
      path: %v
      mode: 448
`

const envoyConfigTemplate = `
%v

dynamic_resources:
  cds_config:
    resource_api_version: V3
    api_config_source:
      api_type: GRPC
      transport_api_version: V3
      grpc_services:
      - envoy_grpc:
          cluster_name: xds_cluster
      set_node_on_first_message_only: true
  lds_config:
    resource_api_version: V3
    api_config_source:
      api_type: GRPC
      transport_api_version: V3
      grpc_services:
      - envoy_grpc:
          cluster_name: xds_cluster
      set_node_on_first_message_only: true
node:
  id: ucloudim_stack
  cluster: ucloudim_cluster
static_resources:
  clusters:
  - connect_timeout: 1s
    load_assignment:
      cluster_name: xds_cluster
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              pipe:
                path: %v
                mode: 448
    http2_protocol_options: {}
    name: xds_cluster
layered_runtime:
  layers:
    - name: runtime-0
      rtds_layer:
        rtds_config:
          resource_api_version: V3
          api_config_source:
            transport_api_version: V3
            api_type: GRPC
            grpc_services:
              envoy_grpc:
                cluster_name: xds_cluster
        name: runtime-0

`

type EnvoyCluster struct {
	Name    string
	Address string
	Port    int
	UseDNS  bool
}

type RouteType int

const (
	RouteTypeUser RouteType = iota
	RouteTypeIngress
	RouteTypeAuthorize
	RouteTypeVnc
)

type EnvoyRoute struct {
	Cluster      string
	Identifier   string
	CustomDomain string
	AuthTokens   []string
	Type         RouteType
}

func (r *EnvoyRoute) weight() int {
	switch r.Type {
	case RouteTypeUser:
		if len(r.Identifier) == 0 {
			return 11
		}
		return 10
	case RouteTypeIngress:
		return 6
	case RouteTypeAuthorize:
		return 5
	case RouteTypeVnc:
		return 5
	}

	return 1000
}

func sortRoutes(routes map[*EnvoyRoute]bool) []*EnvoyRoute {
	var sortedRoutes []*EnvoyRoute
	for route, _ := range routes {
		sortedRoutes = append(sortedRoutes, route)
	}

	sort.Slice(sortedRoutes, func(i, j int) bool {
		leftWeight := sortedRoutes[i].weight()
		rightWeight := sortedRoutes[j].weight()

		return leftWeight < rightWeight
	})
	return sortedRoutes
}
