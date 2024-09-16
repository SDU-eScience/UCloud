package gateway

import (
	_ "embed"
	"encoding/json"
	"fmt"
	accesslogv3 "github.com/envoyproxy/go-control-plane/envoy/config/accesslog/v3"
	core "github.com/envoyproxy/go-control-plane/envoy/config/core/v3"
	endpoint "github.com/envoyproxy/go-control-plane/envoy/config/endpoint/v3"
	route "github.com/envoyproxy/go-control-plane/envoy/config/route/v3"
	jwt "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/http/jwt_authn/v3"
	matcher "github.com/envoyproxy/go-control-plane/envoy/type/matcher/v3"
	"github.com/envoyproxy/go-control-plane/pkg/cache/types"
	"github.com/envoyproxy/go-control-plane/pkg/cache/v3"
	"github.com/envoyproxy/go-control-plane/pkg/resource/v3"
	"github.com/golang/protobuf/ptypes/any"
	"github.com/golang/protobuf/ptypes/duration"
	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/emptypb"
	"strings"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/util"

	cluster "github.com/envoyproxy/go-control-plane/envoy/config/cluster/v3"
	listener "github.com/envoyproxy/go-control-plane/envoy/config/listener/v3"
	router "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/http/router/v3"
	hcm "github.com/envoyproxy/go-control-plane/envoy/extensions/filters/network/http_connection_manager/v3"
)

func createConfigurationSnapshot(
	listenAddress string,
	port int,
	routes []*EnvoyRoute,
	clusters map[string]*EnvoyCluster,
) *cache.Snapshot {
	// NOTE(Dan): Welcome to a file where the noise-to-signal ratio is horrible. Nothing we can do about this, this
	// is simply how Envoy has chosen to do things.

	snap, _ := cache.NewSnapshot(
		util.RandomToken(16),
		map[resource.Type][]types.Resource{
			resource.ClusterType:  createClusters(clusters),
			resource.RouteType:    {createRoutes(routes)},
			resource.ListenerType: {createListener(listenAddress, port)},
		},
	)
	return snap
}

const jwtFilterName = "envoy.filters.http.jwt"

func createListener(listenAddress string, port int) *listener.Listener {
	serializedJwks, err := json.Marshal(cfg.Jwks)
	checkCfg(err)
	jwtAuth := &jwt.JwtAuthentication{
		Providers: map[string]*jwt.JwtProvider{
			"ucloud": {
				Issuer: "cloud.sdu.dk",
				JwksSourceSpecifier: &jwt.JwtProvider_LocalJwks{
					LocalJwks: &core.DataSource{
						Specifier: &core.DataSource_InlineString{
							InlineString: string(serializedJwks),
						},
					},
				},
				Forward:              false,
				ForwardPayloadHeader: "x-jwt-payload",
			},
		},
		Rules: []*jwt.RequirementRule{{
			Match: &route.RouteMatch{
				PathSpecifier: &route.RouteMatch_Prefix{
					Prefix: "/",
				},
			},
			RequirementType: &jwt.RequirementRule_Requires{
				Requires: &jwt.JwtRequirement{
					RequiresType: &jwt.JwtRequirement_RequiresAny{
						RequiresAny: &jwt.JwtRequirementOrList{
							Requirements: []*jwt.JwtRequirement{
								{
									RequiresType: &jwt.JwtRequirement_ProviderName{
										ProviderName: "ucloud",
									},
								},
								{
									RequiresType: &jwt.JwtRequirement_AllowMissing{
										AllowMissing: &emptypb.Empty{},
									},
								},
							},
						},
					},
				},
			},
		}},
	}

	jwtAuthPb, err := anypb.New(jwtAuth)
	checkCfg(err)

	routerConfig, err := anypb.New(&router.Router{})
	checkCfg(err)

	// The following ~70 lines of code does the following things:
	// - Tell Envoy to, please, read route descriptions from this server
	// - Replace the default 503 screen with our custom one (see badGatewayHtml)

	manager := &hcm.HttpConnectionManager{
		CodecType: hcm.HttpConnectionManager_AUTO,
		LocalReplyConfig: &hcm.LocalReplyConfig{
			Mappers: []*hcm.ResponseMapper{
				{
					Filter: &accesslogv3.AccessLogFilter{
						FilterSpecifier: &accesslogv3.AccessLogFilter_StatusCodeFilter{
							StatusCodeFilter: &accesslogv3.StatusCodeFilter{
								Comparison: &accesslogv3.ComparisonFilter{
									Op: accesslogv3.ComparisonFilter_EQ,
									Value: &core.RuntimeUInt32{
										DefaultValue: 503,
										RuntimeKey:   "key_bad_gateway",
									},
								},
							},
						},
					},
					Body: &core.DataSource{
						Specifier: &core.DataSource_InlineString{
							InlineString: string(badGatewayHtml),
						},
					},
					BodyFormatOverride: &core.SubstitutionFormatString{
						Format: &core.SubstitutionFormatString_TextFormatSource{
							TextFormatSource: &core.DataSource{
								Specifier: &core.DataSource_InlineString{
									InlineString: "%LOCAL_REPLY_BODY%",
								},
							},
						},
						ContentType: "text/html; charset=UTF-8",
					},
				},
			},
		},
		HttpFilters: []*hcm.HttpFilter{
			{
				Name: jwtFilterName,
				ConfigType: &hcm.HttpFilter_TypedConfig{
					TypedConfig: jwtAuthPb,
				},
			},
			{
				Name: "envoy.filters.http.router",
				ConfigType: &hcm.HttpFilter_TypedConfig{
					TypedConfig: routerConfig,
				},
			},
		},
		StatPrefix: "ingress_http",
		RouteSpecifier: &hcm.HttpConnectionManager_Rds{
			Rds: &hcm.Rds{
				RouteConfigName: "local_route",
				ConfigSource: &core.ConfigSource{
					ResourceApiVersion: resource.DefaultAPIVersion,
					InitialFetchTimeout: &duration.Duration{
						Seconds: 0,
						Nanos:   1000000 * 500,
					},
					ConfigSourceSpecifier: &core.ConfigSource_ApiConfigSource{
						ApiConfigSource: &core.ApiConfigSource{
							TransportApiVersion:       resource.DefaultAPIVersion,
							ApiType:                   core.ApiConfigSource_GRPC,
							SetNodeOnFirstMessageOnly: true,
							GrpcServices: []*core.GrpcService{{
								TargetSpecifier: &core.GrpcService_EnvoyGrpc_{
									EnvoyGrpc: &core.GrpcService_EnvoyGrpc{ClusterName: "xds_cluster"},
								},
							}},
						},
					},
				},
			},
		},
	}

	managerPb, err := anypb.New(manager)
	checkCfg(err)

	// The last bit here will tell Envoy to:
	// - Listen on the correct network interface and on the correct port
	// - Use the connection manager that we created earlier

	return &listener.Listener{
		Name: "listener_0",
		Address: &core.Address{
			Address: &core.Address_SocketAddress{
				SocketAddress: &core.SocketAddress{
					Protocol: core.SocketAddress_TCP,
					Address:  listenAddress,
					PortSpecifier: &core.SocketAddress_PortValue{
						PortValue: uint32(port),
					},
				},
			},
		},
		FilterChains: []*listener.FilterChain{
			{
				Filters: []*listener.Filter{
					{
						Name: "http-connection-manager",
						ConfigType: &listener.Filter_TypedConfig{
							TypedConfig: managerPb,
						},
					},
				},
			},
		},
	}
}

func createRoutes(routesToAdd []*EnvoyRoute) *route.RouteConfiguration {
	var routes []*route.Route

	for _, r := range routesToAdd {
		newRoutes := formatRoute(r)
		for _, newRoute := range newRoutes {
			routes = append(routes, newRoute)
		}
	}

	// Default route to let UCloud/Core know that the user instance (which was requested) does not exist.
	routes = append(routes, &route.Route{
		Match: &route.RouteMatch{
			PathSpecifier: &route.RouteMatch_Prefix{Prefix: ""},
		},
		Action: &route.Route_DirectResponse{
			DirectResponse: &route.DirectResponseAction{
				Status: 449,
			},
		},
	})

	return &route.RouteConfiguration{
		Name: "local_route",
		VirtualHosts: []*route.VirtualHost{{
			Name:    "local_route",
			Domains: []string{"*"},
			Routes:  routes,
		}},
	}
}

func createClusters(clusters map[string]*EnvoyCluster) []types.Resource {
	var result []types.Resource
	for _, c := range clusters {
		dnsType := cluster.Cluster_STATIC
		if c.UseDNS {
			dnsType = cluster.Cluster_STRICT_DNS
		}

		result = append(result, &cluster.Cluster{
			Name: c.Name,
			ConnectTimeout: &duration.Duration{
				Nanos: 250_000_000,
			},
			LbPolicy: cluster.Cluster_ROUND_ROBIN,
			ClusterDiscoveryType: &cluster.Cluster_Type{
				Type: dnsType,
			},
			// NOTE(Dan): This is probably the most ridiculous part of this entire file.
			LoadAssignment: &endpoint.ClusterLoadAssignment{
				ClusterName: c.Name,
				Endpoints: []*endpoint.LocalityLbEndpoints{{
					LbEndpoints: []*endpoint.LbEndpoint{{
						HostIdentifier: &endpoint.LbEndpoint_Endpoint{
							Endpoint: &endpoint.Endpoint{
								Address: &core.Address{
									Address: &core.Address_SocketAddress{
										SocketAddress: &core.SocketAddress{
											Address: c.Address,
											PortSpecifier: &core.SocketAddress_PortValue{
												PortValue: uint32(c.Port),
											},
										},
									},
								},
							},
						},
					}},
				}},
			},
		})
	}
	return result
}

func formatRoute(r *EnvoyRoute) []*route.Route {
	createBaseRoute := func() *route.Route {
		result := &route.Route{
			RequestHeadersToRemove: []string{
				"Authorization",
			},
			Action: &route.Route_Route{
				Route: &route.RouteAction{
					ClusterSpecifier: &route.RouteAction_Cluster{
						Cluster: r.Cluster,
					},
					Timeout: &duration.Duration{
						Seconds: 0,
					},
					UpgradeConfigs: []*route.RouteAction_UpgradeConfig{{
						UpgradeType: "websocket",
					}},
				},
			},
		}

		if r.EnvoySecretKey != "" {
			result.RequestHeadersToAdd = append(result.RequestHeadersToAdd, &core.HeaderValueOption{
				Header: &core.HeaderValue{
					Key:   "ucloud-secret",
					Value: r.EnvoySecretKey,
				},
				AppendAction: core.HeaderValueOption_OVERWRITE_IF_EXISTS_OR_ADD,
			})
		}
		return result
	}

	disableJwtFilter := func(r *route.Route) {
		m := r.TypedPerFilterConfig
		if m == nil {
			m = make(map[string]*any.Any)
		}

		perRouteFilter := &jwt.PerRouteConfig{
			RequirementSpecifier: &jwt.PerRouteConfig_Disabled{
				Disabled: true,
			},
		}

		routeFilterPb, err := anypb.New(perRouteFilter)
		checkCfg(err)

		m[jwtFilterName] = routeFilterPb

		r.TypedPerFilterConfig = m
	}

	result := createBaseRoute()
	routes := []*route.Route{result}

	switch r.Type {
	case RouteTypeUser:
		if isLaunchingUserInstances {
			if r.Identifier == "" {
				// Service instance
				result.Match = &route.RouteMatch{
					PathSpecifier: &route.RouteMatch_Prefix{
						Prefix: "/",
					},
					Headers: []*route.HeaderMatcher{{
						Name:        "UCloud-Username",
						InvertMatch: true,
						HeaderMatchSpecifier: &route.HeaderMatcher_PresentMatch{
							PresentMatch: true,
						},
					}},
				}
			} else {
				// User instance
				result.Match = &route.RouteMatch{
					PathSpecifier: &route.RouteMatch_Prefix{
						Prefix: "/",
					},
					Headers: []*route.HeaderMatcher{{
						Name: "UCloud-Username",
						HeaderMatchSpecifier: &route.HeaderMatcher_StringMatch{
							StringMatch: exactString(b64(r.Identifier)),
						},
					}},
				}

				r2 := createBaseRoute()
				r2.Match = &route.RouteMatch{
					PathSpecifier: &route.RouteMatch_Prefix{
						Prefix: "/",
					},
					QueryParameters: []*route.QueryParameterMatcher{{
						Name: "usernameHint",
						QueryParameterMatchSpecifier: &route.QueryParameterMatcher_StringMatch{
							StringMatch: exactString(b64(r.Identifier)),
						},
					}},
				}

				routes = append(routes, r2)
			}
		} else {
			result.Match = &route.RouteMatch{
				PathSpecifier: &route.RouteMatch_Prefix{
					Prefix: "/",
				},
			}
		}

	case RouteTypeVnc:
		result.RequestHeadersToRemove = nil
		result.Match = &route.RouteMatch{
			PathSpecifier: &route.RouteMatch_Prefix{
				Prefix: fmt.Sprintf("/ucloud/%v/vnc", cfg.Provider.Id),
			},
			QueryParameters: []*route.QueryParameterMatcher{{
				Name: "token",
				QueryParameterMatchSpecifier: &route.QueryParameterMatcher_StringMatch{
					StringMatch: exactString(r.Identifier),
				},
			}},
		}

	case RouteTypeAuthorize:
		result.RequestHeadersToRemove = nil
		disableJwtFilter(result)
		result.Match = &route.RouteMatch{
			PathSpecifier: &route.RouteMatch_Prefix{
				Prefix: fmt.Sprintf("/ucloud/%v/authorize-app", cfg.Provider.Id),
			},
		}

	case RouteTypeIngress:
		result.RequestHeadersToRemove = nil
		disableJwtFilter(result)
		matchers := []*route.HeaderMatcher{{
			Name: ":authority",
			HeaderMatchSpecifier: &route.HeaderMatcher_StringMatch{
				StringMatch: exactString(r.CustomDomain),
			},
		}}

		if len(r.AuthTokens) > 0 {
			var regexBuilder strings.Builder
			regexBuilder.WriteString(".*ucloud-compute-session-.*=(")
			for i, elem := range r.AuthTokens {
				if i > 0 {
					regexBuilder.WriteString("|")
				}

				regexBuilder.WriteString(urlEncode(elem))
			}
			regexBuilder.WriteString(").*")

			matchers = append(matchers, &route.HeaderMatcher{
				Name: "cookie",
				HeaderMatchSpecifier: &route.HeaderMatcher_StringMatch{
					StringMatch: &matcher.StringMatcher{
						MatchPattern: &matcher.StringMatcher_SafeRegex{
							SafeRegex: &matcher.RegexMatcher{
								Regex: regexBuilder.String(),
							},
						},
					},
				},
			})
		}

		result.Match = &route.RouteMatch{
			PathSpecifier: &route.RouteMatch_Prefix{
				Prefix: "/",
			},
			Headers: matchers,
		}
	}

	return routes
}

func exactString(match string) *matcher.StringMatcher {
	return &matcher.StringMatcher{
		MatchPattern: &matcher.StringMatcher_Exact{
			Exact: match,
		},
	}
}

func checkCfg(err error) {
	if err != nil {
		panic("envoy config_generator fail: " + err.Error())
	}
}
