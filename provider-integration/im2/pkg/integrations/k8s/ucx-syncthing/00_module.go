package ucx_syncthing

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

const (
	defaultStateDir       = "/syncthing-state"
	defaultSyncthingURL   = "http://127.0.0.1:8384"
	defaultHealthInterval = 5 * time.Second
	defaultHealthGrace    = 60 * time.Second
)

type runtimeConfig struct {
	port           int
	stateDir       string
	baseURL        string
	healthInterval time.Duration
	healthGrace    time.Duration
}

func Launch() {
	cfg, err := loadRuntimeConfig()
	if err != nil {
		log.Fatal("UCX Syncthing: %s", err)
	}

	store := newStateStore()
	api := newAPIRuntime(cfg)
	go runCollector(context.Background(), cfg, api, store)
	ucx.AppServe(func() ucx.Application {
		return newApplication(store, api)
	}, util.OptValue(cfg.port))
}

func loadRuntimeConfig() (runtimeConfig, error) {
	port, err := parsePort(os.Getenv("UCX_PORT"))
	if err != nil {
		return runtimeConfig{}, err
	}

	stateDir := strings.TrimSpace(os.Getenv("STATE_DIR"))
	if stateDir == "" {
		stateDir = defaultStateDir
	}
	healthInterval, err := parseDurationSetting("UCX_SYNCTHING_HEALTH_INTERVAL", defaultHealthInterval, time.Second, time.Minute)
	if err != nil {
		return runtimeConfig{}, err
	}
	healthGrace, err := parseDurationSetting("UCX_SYNCTHING_HEALTH_GRACE", defaultHealthGrace, healthInterval, 30*time.Minute)
	if err != nil {
		return runtimeConfig{}, err
	}

	return runtimeConfig{
		port:           port,
		stateDir:       stateDir,
		baseURL:        defaultSyncthingURL,
		healthInterval: healthInterval,
		healthGrace:    healthGrace,
	}, nil
}

func parsePort(value string) (int, error) {
	port, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || port < 1 || port > 65535 {
		return 0, fmt.Errorf("UCX_PORT must be an integer between 1 and 65535")
	}
	return port, nil
}

func parseDurationSetting(name string, fallback time.Duration, min time.Duration, max time.Duration) (time.Duration, error) {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback, nil
	}
	value, err := time.ParseDuration(raw)
	if err != nil || value < min || value > max {
		return 0, fmt.Errorf("%s must be between %s and %s", name, min, max)
	}
	return value, nil
}
