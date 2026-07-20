package syncthing_metrics

import "ucloud.dk/shared/pkg/rpc"

const SchemaVersion = 1

// Snapshot is fixed-shape and contains no instance, folder, or device identifiers.
type Snapshot struct {
	SchemaVersion            int     `json:"schemaVersion"`
	PreparedAt               string  `json:"preparedAt"`
	HealthyInstances         int64   `json:"healthyInstances"`
	DegradedInstances        int64   `json:"degradedInstances"`
	RestartRequiredInstances int64   `json:"restartRequiredInstances"`
	OnlineDevices            int64   `json:"onlineDevices"`
	OfflineDevices           int64   `json:"offlineDevices"`
	OutOfSyncItems           int64   `json:"outOfSyncItems"`
	OutOfSyncBytes           int64   `json:"outOfSyncBytes"`
	SendBytesPerSecond       float64 `json:"sendBytesPerSecond"`
	ReceiveBytesPerSecond    float64 `json:"receiveBytesPerSecond"`
	FoldersWithErrors        int64   `json:"foldersWithErrors"`
	DevicesWithErrors        int64   `json:"devicesWithErrors"`
	TelemetryStale           bool    `json:"telemetryStale"`
	Truncated                bool    `json:"truncated"`
}

type PublishRequest struct {
	Token    string   `json:"token"`
	Snapshot Snapshot `json:"snapshot"`
}

type PublishResponse struct{}

var Publish = rpc.Call[PublishRequest, PublishResponse]{
	BaseContext: "internal/job-introspection",
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "syncthingMetrics",
}
