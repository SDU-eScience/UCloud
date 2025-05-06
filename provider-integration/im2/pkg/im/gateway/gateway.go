package gateway

import (
	"bytes"
	_ "embed"
	"encoding/base64"
	"encoding/gob"
	"fmt"
	"github.com/envoyproxy/go-control-plane/pkg/cache/types"
	"github.com/envoyproxy/go-control-plane/pkg/resource/v3"
	"gopkg.in/yaml.v3"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"sync/atomic"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/im/ipc"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
	"unicode"
)

const fileConfig = "config.yaml"

//go:embed bad-gateway.html
var badGatewayHtml []byte

const ServerClusterName = "_UCloud"
const ServerClusterPort = 42000

var ipcGwDumpConfiguration = ipc.NewCall[util.Empty, string]("imgw.dump")

type ConfigurationMessage struct {
	ClusterUp              *EnvoyCluster
	ClusterDown            *EnvoyCluster
	RouteUp                *EnvoyRoute
	RouteDown              *EnvoyRoute
	LaunchingUserInstances *bool
}

type Config struct {
	ListenAddress string
	Port          int
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
		adminSection := ""
		if util.DevelopmentModeEnabled() {
			adminSection = adminSectionDev
		} else {
			adminSection = fmt.Sprintf(adminSectionProd, filepath.Join(stateDir, "admin.sock"))
		}

		xdsSection := ""
		switch cfg.Provider.Envoy.ListenMode {
		case cfg.EnvoyListenModeTcp:
			xdsSection = fmt.Sprintf(xdsTcp, cfg.Provider.Hosts.Self.Address)
			adminSection = ""
		case cfg.EnvoyListenModeUnix:
			xdsSection = fmt.Sprintf(xdsUnix, filepath.Join(stateDir, "xds.sock"))
		}

		err := os.WriteFile(
			fmt.Sprintf("%v/%v", stateDir, fileConfig),
			[]byte(
				fmt.Sprintf(
					envoyConfigTemplate,
					adminSection,
					xdsSection,
				)),
			0o600,
		)

		if err != nil {
			log.Error("Failed to write required configuration files for the gateway: %v", err)
			os.Exit(1)
		}
	}

	routes[&EnvoyRoute{
		Type:           RouteTypeUser,
		Cluster:        ServerClusterName,
		Identifier:     "",
		EnvoySecretKey: cfg.OwnEnvoySecret,
	}] = true

	routes[&EnvoyRoute{
		Type:           RouteTypeAuthorize,
		Cluster:        ServerClusterName,
		EnvoySecretKey: cfg.OwnEnvoySecret,
	}] = true

	clusters[ServerClusterName] = &EnvoyCluster{
		Name:    ServerClusterName,
		Address: internalAddress,
		Port:    ServerClusterPort,
		UseDNS:  !unicode.IsDigit([]rune(internalAddress)[0]),
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

func InitIpc() {
	ipcGwDumpConfiguration.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[string] {
		if r.Uid != 0 {
			return ipc.Response[string]{
				StatusCode: http.StatusForbidden,
			}
		}

		snapshot := mostRecentSnapshot
		if snapshot == nil {
			return ipc.Response[string]{
				StatusCode:   http.StatusOK,
				ErrorMessage: "message: no snapshot",
			}
		}

		routes := snapshot.GetResources(resource.RouteType)
		clusters := snapshot.GetResources(resource.ClusterType)
		data, err := yaml.Marshal(map[string]map[string]types.Resource{"routes": routes, "clusters": clusters})
		if err != nil {
			return ipc.Response[string]{
				StatusCode:   http.StatusInternalServerError,
				ErrorMessage: "error: " + err.Error(),
			}
		}

		return ipc.Response[string]{
			StatusCode: http.StatusOK,
			Payload:    string(data),
		}
	})
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

const xdsUnix = `
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
`

const xdsTcp = `
  - connect_timeout: 1s
    load_assignment:
      cluster_name: xds_cluster
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: %v
                port_value: 52033
    http2_protocol_options: {}
    name: xds_cluster
    type: STRICT_DNS
`

const envoyConfigTemplate = `
%v

# NOTE: Setting the initial_fetch_timeout to a low value since it seems to always be hitting the timeout the first time around.
dynamic_resources:
  cds_config:
    resource_api_version: V3
    initial_fetch_timeout: 0.005s
    api_config_source:
      api_type: GRPC
      transport_api_version: V3
      grpc_services:
      - envoy_grpc:
          cluster_name: xds_cluster
      set_node_on_first_message_only: true
  lds_config:
    resource_api_version: V3
    initial_fetch_timeout: 0.005s
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
%v
layered_runtime:
  layers:
    - name: runtime-0
      rtds_layer:
        rtds_config:
          resource_api_version: V3
          initial_fetch_timeout: 0.005s
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
	Cluster        string
	Identifier     string
	CustomDomain   string
	AuthTokens     []string
	Type           RouteType
	EnvoySecretKey string
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
