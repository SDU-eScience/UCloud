package gateway

import (
	"bytes"
	_ "embed"
	"encoding/base64"
	"encoding/gob"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"sync/atomic"
	"time"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
	"unicode"
)

const fileClusters = "clusters.yaml"
const fileRds = "rds.yaml"
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
var isLaunchingUserInstances bool = false

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
		if err == nil {
			err = os.WriteFile(
				fmt.Sprintf("%v/%v", stateDir, fileBadGateway),
				badGatewayHtml,
				0o600,
			)
		}

		if err == nil {
			err = os.WriteFile(
				fmt.Sprintf("%v/%v", stateDir, fileConfig),
				[]byte(fmt.Sprintf(
					envoyConfigTemplate,
					fmt.Sprintf("%v/%v", stateDir, fileClusters),
					config.ListenAddress,
					config.Port,
					fmt.Sprintf("%v/%v", stateDir, fileBadGateway),
					fmt.Sprintf("%v/%v", stateDir, fileRds),
				)),
				0o600,
			)
		}

		if err == nil {
			err = os.WriteFile(
				fmt.Sprintf("%v/%v", stateDir, fileRds),
				[]byte("{}"),
				0o600,
			)
		}

		if err == nil {
			err = os.WriteFile(
				fmt.Sprintf("%v/%v", stateDir, fileClusters),
				[]byte("{}"),
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
				version := fmt.Sprintf("%x%x%x", rand.Int63(), rand.Int63(), rand.Int63())
				err := os.WriteFile(
					fmt.Sprintf("%v/%v%v", stateDir, version, fileRds),
					[]byte(formatRoutes(version, routes)),
					0o600,
				)

				if err == nil {
					err = os.WriteFile(
						fmt.Sprintf("%v/%v%v", stateDir, version, fileClusters),
						[]byte(formatClusters(version, clusters)),
						0o600,
					)
				}

				if err == nil {
					err = os.Rename(
						fmt.Sprintf("%v/%v%v", stateDir, version, fileRds),
						fmt.Sprintf("%v/%v", stateDir, fileRds),
					)
				}

				if err == nil {
					err = os.Rename(
						fmt.Sprintf("%v/%v%v", stateDir, version, fileClusters),
						fmt.Sprintf("%v/%v", stateDir, fileClusters),
					)
				}

				if err != nil {
					log.Warn("Failed to write configuration files for the gateway: %v", err)
				}
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

					cmd.Env = append(cmd.Env, "ENVOY_VERSION=1.23.0")
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

func jsonify(value any) string {
	d, _ := json.Marshal(value)
	if d == nil {
		return ""
	} else {
		return string(d)
	}
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
const envoyConfigTemplate = `
dynamic_resources:
  cds_config:
    path: %v

node:
  cluster: ucloudim_cluster
  id: ucloudim_stack

admin:
  access_log_path: "/dev/stdout"
  
layered_runtime:
  layers:
  - name: static_layer_0
    static_layer:
      envoy:
        resource_limits:
          listener:
            example_listener_name:
              connection_limit: 10000
      overload:
        global_downstream_max_connections: 50000

static_resources:
  listeners:
    - address:
        socket_address:
          address: %v
          port_value: %v
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                codec_type: auto
                local_reply_config:
                  mappers:
                    - filter:
                        status_code_filter:
                          comparison:
                            op: EQ
                            value:
                              default_value: 503
                              runtime_key: key_bad_gateway
                      body:
                        filename: %v
                      body_format_override:
                        text_format: "%%LOCAL_REPLY_BODY%%"
                        content_type: "text/html; charset=UTF-8"

                http_filters:
                - name: envoy.filters.http.router
                  typed_config:
                    "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
                stat_prefix: ingress_http
                rds:
                  route_config_name: local_route
                  config_source:
                    path: %v
`

type EnvoyCluster struct {
	Name    string
	Address string
	Port    int
	UseDNS  bool
}

const envoyClusterTemplate = `
{
    "name": %v,
    "connect_timeout": "0.25s",
    "@type": "type.googleapis.com/envoy.config.cluster.v3.Cluster",
    "lb_policy": "ROUND_ROBIN",
    "type": %v ,
    "upstream_connection_options": {
        "tcp_keepalive": {}
    },
    "load_assignment": {
        "cluster_name": %v,
        "endpoints": [
            {
                "lb_endpoints": [
                    {
                        "endpoint": {
                            "address": {
                                "socket_address": {
                                    "address": %v,
                                    "port_value": %v
                                }
                            }
                        }
                    }
                ]
            }
        ]
    }
}
`

func (c *EnvoyCluster) formatCluster() string {
	dnsType := "STATIC"
	if c.UseDNS {
		dnsType = "STRICT_DNS"
	}

	return fmt.Sprintf(
		envoyClusterTemplate,
		jsonify(c.Name),
		jsonify(dnsType),
		jsonify(c.Name),
		jsonify(c.Address),
		jsonify(c.Port),
	)
}

const clustersTemplate = `{
	"version_info": %v,
	"resources": [%v]
}`

func formatClusters(version string, clusters map[string]*EnvoyCluster) string {
	var mappedClusters []string = nil
	for _, cluster := range clusters {
		mappedClusters = append(mappedClusters, cluster.formatCluster())
	}

	return fmt.Sprintf(clustersTemplate, jsonify(version), strings.Join(mappedClusters, ","))
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

const standardRouteTemplate = `{
	"cluster": %v,
	"timeout": { "seconds": 0 },
	"upgrade_configs": [{ "upgrade_type": "websocket", "enabled": true }]
}`

const envoyRouteTemplateServiceOnly = `{
	"route": %v,
	"match": { "prefix": "/" }
}`

const envoyRouteTemplateRealUserService = `{
	"route": %v,
	"match": {
		"prefix": "/",
		"headers": [{
			"name": "UCloud-Username",
			"invert_match": true,
			"present_match": true
		}]
	}
}`

const envoyRouteTemplateRealUserHeader = `{
	"route": %v,
	"match": {
		"prefix": "/",
		"headers": [{
			"name": "UCloud-Username",
			"exact_match": "%v"
		}]
	}
}`

const envoyRouteTemplateRealUserQueryParam = `{
	"route": %v,
	"match": {
		"prefix": "/",
		"query_parameters": [{
			"name": "usernameHint",
			"string_match": { "exact": "%v" }
		}]
	}
}`

const envoyRouteTemplateVnc = `{
	"route": %v,
	"match": {
		"path": %v,
		"query_parameters": [{
			"name": "token",
			"string_match": { "exact": "%v" }
		}]
	}
}`

const envoyRouteTemplateAuthorize = `{
	"route": %v,
	"match": { "prefix": %v }
}`

const envoyRouteTemplateIngress = `{
	"route": %v,
	"match": {
		"prefix": "/",
		"headers": [
			{ "name": ":authority", "exact_match": %v }
			%v
		]
	}
}`

const envoyRouteTemplateIngressCookieEntry = `, {
	"name": "cookie",
	"string_match": {
		"safe_regex": { "regex": %v } }
	}
}`

func (c *EnvoyRoute) formatRoute() []string {
	var result []string = nil

	route := fmt.Sprintf(standardRouteTemplate, jsonify(c.Cluster))

	switch c.Type {
	case RouteTypeUser:
		if isLaunchingUserInstances {
			if len(c.Identifier) == 0 {
				// Service instance
				result = append(
					result,
					fmt.Sprintf(
						envoyRouteTemplateRealUserService,
						route,
					),
				)
			} else {
				// User instance
				result = append(
					result,
					fmt.Sprintf(
						envoyRouteTemplateRealUserHeader,
						route,
						b64(c.Identifier),
					),
				)

				result = append(
					result,
					fmt.Sprintf(
						envoyRouteTemplateRealUserQueryParam,
						route,
						b64(c.Identifier),
					),
				)
			}
		} else {
			result = append(
				result,
				fmt.Sprintf(
					envoyRouteTemplateServiceOnly,
					route,
				),
			)
		}
	case RouteTypeVnc:
		result = append(
			result,
			fmt.Sprintf(
				envoyRouteTemplateVnc,
				route,
				jsonify(fmt.Sprintf("/ucloud/%v/vnc", cfg.Provider.Id)),
				jsonify(c.Identifier),
			),
		)
	case RouteTypeAuthorize:
		result = append(
			result,
			fmt.Sprintf(
				envoyRouteTemplateAuthorize,
				route,
				jsonify(fmt.Sprintf("/ucloud/%v/authorize-app", cfg.Provider.Id)),
			),
		)
	case RouteTypeIngress:
		var cookieMatcher = ""
		if len(c.AuthTokens) > 0 {
			var regexBuilder strings.Builder
			for i, elem := range c.AuthTokens {
				if i > 0 {
					regexBuilder.WriteString("|")
				}

				regexBuilder.WriteString(urlEncode(elem))
			}

			cookieMatcher = fmt.Sprintf(
				envoyRouteTemplateIngressCookieEntry,
				jsonify(regexBuilder.String()),
			)
		}

		result = append(
			result,
			fmt.Sprintf(
				envoyRouteTemplateIngress,
				route,
				c.CustomDomain,
				cookieMatcher,
			),
		)
	}

	return result
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

const routesTemplate = `{
	"version_info": %v,
	"resources": [
		{
			"@type": "type.googleapis.com/envoy.config.route.v3.RouteConfiguration",
			"name": "local_route",
			"virtual_hosts": [
				{
					"name": "local_route",
					"domains": ["*"],
					"routes": [
						%v,
						{
							"match": { "prefix": "" },
							"direct_response": { "status": 449 }
						}
					]
				}
			]
		}
	]
}`

func formatRoutes(version string, routes map[*EnvoyRoute]bool) string {
	// NOTE(Dan): We must ensure that the sessions are routed with a higher priority, otherwise the traffic will
	//always go to the wrong route.
	var sortedRoutes []*EnvoyRoute
	for route, _ := range routes {
		sortedRoutes = append(sortedRoutes, route)
	}

	sort.Slice(sortedRoutes, func(i, j int) bool {
		leftWeight := sortedRoutes[i].weight()
		rightWeight := sortedRoutes[j].weight()

		return leftWeight < rightWeight
	})

	var mappedRoutes []string = nil
	for _, route := range sortedRoutes {
		res := route.formatRoute()
		for _, s := range res {
			mappedRoutes = append(mappedRoutes, s)
		}
	}

	return fmt.Sprintf(routesTemplate, jsonify(version), strings.Join(mappedRoutes, ","))
}
