package gateway

import (
	"context"
	"net"
	"os"
	"path/filepath"
	"sync/atomic"
	"time"

	"github.com/envoyproxy/go-control-plane/pkg/cache/v3"
	elog "github.com/envoyproxy/go-control-plane/pkg/log"
	"github.com/envoyproxy/go-control-plane/pkg/server/v3"
	"google.golang.org/grpc"
	"google.golang.org/grpc/keepalive"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/shared/pkg/log"

	clusterservice "github.com/envoyproxy/go-control-plane/envoy/service/cluster/v3"
	discoverygrpc "github.com/envoyproxy/go-control-plane/envoy/service/discovery/v3"
	endpointservice "github.com/envoyproxy/go-control-plane/envoy/service/endpoint/v3"
	listenerservice "github.com/envoyproxy/go-control-plane/envoy/service/listener/v3"
	routeservice "github.com/envoyproxy/go-control-plane/envoy/service/route/v3"
	runtimeservice "github.com/envoyproxy/go-control-plane/envoy/service/runtime/v3"
	secretservice "github.com/envoyproxy/go-control-plane/envoy/service/secret/v3"
)

const (
	grpcKeepaliveTime        = 30 * time.Second
	grpcKeepaliveTimeout     = 5 * time.Second
	grpcKeepaliveMinTime     = 30 * time.Second
	grpcMaxConcurrentStreams = 1000000
	port                     = 41492
	nodeId                   = "ucloudim_stack"
)

var envoyCache cache.SnapshotCache = nil
var xdsServer server.Server = nil

func initSnapshotCache() {
	envoyCache = cache.NewSnapshotCache(false, cache.IDHash{}, createEnvoyLogger())
}

func startConfigurationServer() {
	var lis net.Listener
	var err error

	switch cfg.Provider.Envoy.ListenMode {
	case cfg.EnvoyListenModeUnix:
		socketPath := filepath.Join(cfg.Provider.Envoy.StateDirectory, "xds.sock")
		_ = os.Remove(socketPath)
		lis, err = net.Listen("unix", socketPath)
		if err != nil {
			log.Fatal("UCloud/Gateway configuration server failed to start! Fatal error! %v", err)
			os.Exit(1)
		}

		err = os.Chmod(socketPath, 0700)
		if err != nil {
			log.Fatal("UCloud/Gateway configuration server failed to start! Fatal error! %v", err)
			os.Exit(1)
		}

	case cfg.EnvoyListenModeTcp:
		lis, err = net.Listen("tcp", "0.0.0.0:52033")
		if err != nil {
			log.Fatal("UCloud/Gateway configuration server failed to start! Fatal error! %v", err)
			os.Exit(1)
		}

	default:
		log.Fatal("UCloud/Gateway configuration server has no listen mode configured!")
		return
	}

	grpcServer := func() *grpc.Server {
		var grpcOptions []grpc.ServerOption
		grpcOptions = append(grpcOptions,
			grpc.MaxConcurrentStreams(grpcMaxConcurrentStreams),
			grpc.KeepaliveParams(keepalive.ServerParameters{
				Time:    grpcKeepaliveTime,
				Timeout: grpcKeepaliveTimeout,
			}),
			grpc.KeepaliveEnforcementPolicy(keepalive.EnforcementPolicy{
				MinTime:             grpcKeepaliveMinTime,
				PermitWithoutStream: true,
			}),
		)
		return grpc.NewServer(grpcOptions...)
	}()

	xdsServer = server.NewServer(context.Background(), envoyCache, nil)

	{
		discoverygrpc.RegisterAggregatedDiscoveryServiceServer(grpcServer, xdsServer)
		endpointservice.RegisterEndpointDiscoveryServiceServer(grpcServer, xdsServer)
		clusterservice.RegisterClusterDiscoveryServiceServer(grpcServer, xdsServer)
		routeservice.RegisterRouteDiscoveryServiceServer(grpcServer, xdsServer)
		listenerservice.RegisterListenerDiscoveryServiceServer(grpcServer, xdsServer)
		secretservice.RegisterSecretDiscoveryServiceServer(grpcServer, xdsServer)
		runtimeservice.RegisterRuntimeDiscoveryServiceServer(grpcServer, xdsServer)
	}

	if err = grpcServer.Serve(lis); err != nil {
		log.Fatal("UCloud/Gateway configuration server has died unexpectedly! Fatal error! %v", err)
	}
}

var mostRecentSnapshot atomic.Pointer[cache.Snapshot]

func setActiveSnapshot(snapshot *cache.Snapshot) {
	err := snapshot.Consistent()
	if err != nil {
		log.Fatal("UCloud/Gateway snapshot is invalid: %v", err)
		panic("Invalid snapshot. Fatal error!")
	}

	err = envoyCache.SetSnapshot(context.Background(), nodeId, snapshot)
	if err != nil {
		log.Fatal("UCloud/Gateway failed to update snapshot: %v", err)
		panic("Invalid snapshot. Fatal error!")
	}

	mostRecentSnapshot.Store(snapshot)
}

func createEnvoyLogger() elog.Logger {
	logCfg := &cfg.Provider.Logs
	l := log.NewLogger(log.LevelInfo, false)

	err := l.SetLogFile(filepath.Join(logCfg.Directory, "envoy-xds.log"))
	if err != nil {
		panic("Failed to create a logger for Envoy XDS! bad permissions in " + logCfg.Directory)
	}

	if logCfg.Rotation.Enabled {
		l.SetRotation(log.RotateDaily, logCfg.Rotation.RetentionPeriodInDays, true)
	}

	return &elogger{logger: l}
}

type elogger struct {
	logger *log.Logger
}

func (e *elogger) Debugf(format string, args ...interface{}) {
	e.logger.Debug(format, args...)
}

func (e *elogger) Infof(format string, args ...interface{}) {
	e.logger.Info(format, args...)
}

func (e *elogger) Warnf(format string, args ...interface{}) {
	e.logger.Warn(format, args...)
}

func (e *elogger) Errorf(format string, args ...interface{}) {
	e.logger.Error(format, args...)
}
