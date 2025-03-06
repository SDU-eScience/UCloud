package main

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"fmt"
	"io"
	"log"
	"net/http"
	"path"
	"strings"
)

type SyncthingConfigXML struct {
	XMLName xml.Name `xml:"configuration"`
	GUI     struct {
		APIKey string `xml:"apikey"`
	} `xml:"gui"`
	Device []struct {
		ID   string `xml:"id,attr"`
		Name string `xml:"name,attr"`
	} `xml:"device"`
	Folder []struct {
		ID string `xml:"id,attr"`
	} `xml:"folder"`
}

type SyncthingClient struct {
	apiKey     string
	httpClient *http.Client
	logger     *log.Logger
}

func NewSyncthingClient(apiKey string) *SyncthingClient {
	return &SyncthingClient{
		apiKey:     apiKey,
		httpClient: &http.Client{},
		logger:     log.Default(),
	}
}

func (c *SyncthingClient) deviceEndpoint(path string) string {
	withoutLeadingSlash, _ := strings.CutPrefix(path, "/")
	return fmt.Sprintf("http://localhost:8384/%s", withoutLeadingSlash)
}

func (c *SyncthingClient) makeRequest(method, url string, body interface{}) (*http.Response, error) {
	var bodyReader io.Reader
	if body != nil {
		jsonBody, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %v", err)
		}
		bodyReader = bytes.NewBuffer(jsonBody)
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %v", err)
	}

	req.Header.Set("X-API-Key", c.apiKey)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		c.logger.Printf("%v %v -> %v", method, url, err)
	} else {
		c.logger.Printf("%v %v -> %v", method, url, resp.StatusCode)
	}
	return resp, err
}

func (c *SyncthingClient) handleResponse(resp *http.Response) error {
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		res := fmt.Errorf("API request failed with status %d: %s", resp.StatusCode, string(bodyBytes))
		c.logger.Println(res)
		return res
	}
	return nil
}

func (c *SyncthingClient) AddFolders(folders []UCloudSyncthingConfigFolder, devices []UCloudSyncthingConfigDevice) error {
	c.logger.Println("Adding folders", folders)

	if len(folders) == 0 {
		return nil
	}

	var newFolders []SyncthingFolder
	for _, folder := range folders {
		var folderDevices []SyncthingFolderDevice
		for _, device := range devices {
			folderDevices = append(folderDevices, SyncthingFolderDevice{
				DeviceID: device.DeviceID,
			})
		}

		filePath := folder.Path()

		newFolders = append(newFolders, SyncthingFolder{
			ID:              folder.ID,
			Label:           path.Base(filePath),
			Devices:         folderDevices,
			Path:            filePath,
			Type:            SynchronizationTypeSendReceive.String(),
			RescanIntervalS: 3600,
		})
	}

	resp, err := c.makeRequest(
		http.MethodPut,
		c.deviceEndpoint("/rest/config/folders"),
		newFolders,
	)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp)
}

func (c *SyncthingClient) RemoveFolders(folders []UCloudSyncthingConfigFolder) error {
	c.logger.Println("Removing folders")

	if len(folders) == 0 {
		return nil
	}

	for _, folder := range folders {
		resp, err := c.makeRequest(
			http.MethodDelete,
			c.deviceEndpoint(fmt.Sprintf("/rest/config/folders/%s", folder.ID)),
			nil,
		)
		if err != nil {
			return err
		}
		defer resp.Body.Close()

		if err := c.handleResponse(resp); err != nil {
			return err
		}
	}

	return nil
}

func (c *SyncthingClient) AddDevices(devices []UCloudSyncthingConfigDevice) error {
	c.logger.Println("Adding devices", devices)

	if len(devices) == 0 {
		return nil
	}

	var newDevices []SyncthingDevice
	for _, device := range devices {
		newDevices = append(newDevices, SyncthingDevice{
			DeviceID: device.DeviceID,
			Name:     device.Label,
		})
	}

	resp, err := c.makeRequest(
		http.MethodPut,
		c.deviceEndpoint("/rest/config/devices"),
		newDevices,
	)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp)
}

func (c *SyncthingClient) RemoveDevices(devices []string) error {
	if len(devices) == 0 {
		return nil
	}

	c.logger.Println("Removing devices")

	for _, device := range devices {
		resp, err := c.makeRequest(
			http.MethodDelete,
			c.deviceEndpoint(fmt.Sprintf("/rest/config/devices/%s", device)),
			nil,
		)
		if err != nil {
			return err
		}
		defer resp.Body.Close()

		// Not throwing an error if device is not available
		c.handleResponse(resp)
	}

	return nil
}

func (c *SyncthingClient) ConfigureOptions(opts SyncthingOptions) error {
	resp, err := c.makeRequest(
		http.MethodPut,
		c.deviceEndpoint("/rest/config/options"),
		opts,
	)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp)
}

func (c *SyncthingClient) ConfigureGui(opts SyncthingGui) error {
	resp, err := c.makeRequest(
		http.MethodPatch,
		c.deviceEndpoint("/rest/config/gui"),
		opts,
	)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	return c.handleResponse(resp)
}

type SynchronizationType int

const (
	SynchronizationTypeSendReceive SynchronizationType = iota
	SynchronizationTypeSendOnly
)

func (st SynchronizationType) String() string {
	switch st {
	case SynchronizationTypeSendReceive:
		return "sendreceive"
	case SynchronizationTypeSendOnly:
		return "sendonly"
	default:
		return ""
	}
}

type SyncthingIgnoredFolder struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Time  string `json:"time"`
}

type SyncthingRemoteIgnoredDevices struct {
	Address  string `json:"address"`
	DeviceID string `json:"deviceID"`
	Name     string `json:"name"`
	Time     string `json:"time"`
}

type SyncthingDevice struct {
	Addresses                []string                 `json:"addresses,omitempty"`
	AllowedNetworks          []string                 `json:"allowedNetworks,omitempty"`
	AutoAcceptFolders        bool                     `json:"autoAcceptFolders,omitempty"`
	CertName                 string                   `json:"certName,omitempty"`
	Compression              string                   `json:"compression,omitempty"`
	DeviceID                 string                   `json:"deviceID,omitempty"`
	IgnoredFolders           []SyncthingIgnoredFolder `json:"ignoredFolders,omitempty"`
	IntroducedBy             string                   `json:"introducedBy,omitempty"`
	Introducer               bool                     `json:"introducer,omitempty"`
	MaxRecvKbps              int                      `json:"maxRecvKbps,omitempty"`
	MaxRequestKiB            int                      `json:"maxRequestKiB,omitempty"`
	MaxSendKbps              int                      `json:"maxSendKbps,omitempty"`
	Name                     string                   `json:"name,omitempty"`
	Paused                   bool                     `json:"paused,omitempty"`
	RemoteGUIPort            int                      `json:"remoteGUIPort,omitempty"`
	SkipIntroductionRemovals bool                     `json:"skipIntroductionRemovals,omitempty"`
	Untrusted                bool                     `json:"untrusted,omitempty"`
}

type SyncthingDefaults struct {
	Device SyncthingDevice `json:"device,omitempty"`
	Folder SyncthingFolder `json:"folder,omitempty"`
}

type SyncthingGui struct {
	Debugging           bool `json:"debugging,omitempty"`
	Enabled             bool `json:"enabled,omitempty"`
	InsecureAdminAccess bool `json:"insecureAdminAccess,omitempty"`
}

type SyncthingLdap struct {
	Address            string `json:"address,omitempty"`
	BindDN             string `json:"bindDN,omitempty"`
	InsecureSkipVerify bool   `json:"insecureSkipVerify,omitempty"`
	SearchBaseDN       string `json:"searchBaseDN,omitempty"`
	SearchFilter       string `json:"searchFilter,omitempty"`
	Transport          string `json:"transport,omitempty"`
}

type MinDiskFree struct {
	Unit  string `json:"unit,omitempty"`
	Value int    `json:"value,omitempty"`
}

type Versioning struct {
	CleanupIntervalS int               `json:"cleanupIntervalS,omitempty"`
	FsPath           string            `json:"fsPath,omitempty"`
	FsType           string            `json:"fsType,omitempty"`
	Params           map[string]string `json:"params,omitempty"`
	Type             string            `json:"type,omitempty"`
}

type SyncthingFolderDevice struct {
	DeviceID           string `json:"deviceID"`
	EncryptionPassword string `json:"encryptionPassword,omitempty"`
	IntroducedBy       string `json:"introducedBy,omitempty"`
}

type SyncthingFolder struct {
	AutoNormalize           bool                    `json:"autoNormalize,omitempty"`
	BlockPullOrder          string                  `json:"blockPullOrder,omitempty"`
	CaseSensitiveFS         bool                    `json:"caseSensitiveFS,omitempty"`
	Copiers                 int                     `json:"copiers,omitempty"`
	CopyOwnershipFromParent bool                    `json:"copyOwnershipFromParent,omitempty"`
	CopyRangeMethod         string                  `json:"copyRangeMethod,omitempty"`
	Devices                 []SyncthingFolderDevice `json:"devices,omitempty"`
	DisableFsync            bool                    `json:"disableFsync,omitempty"`
	DisableSparseFiles      bool                    `json:"disableSparseFiles,omitempty"`
	DisableTempIndexes      bool                    `json:"disableTempIndexes,omitempty"`
	FilesystemType          string                  `json:"filesystemType,omitempty"`
	FsWatcherDelayS         int                     `json:"fsWatcherDelayS,omitempty"`
	FsWatcherEnabled        bool                    `json:"fsWatcherEnabled,omitempty"`
	Hashers                 int                     `json:"hashers,omitempty"`
	ID                      string                  `json:"id,omitempty"`
	IgnoreDelete            bool                    `json:"ignoreDelete,omitempty"`
	IgnorePerms             bool                    `json:"ignorePerms,omitempty"`
	JunctionsAsDirs         bool                    `json:"junctionsAsDirs,omitempty"`
	Label                   string                  `json:"label,omitempty"`
	MarkerName              string                  `json:"markerName,omitempty"`
	MaxConcurrentWrites     int                     `json:"maxConcurrentWrites,omitempty"`
	MaxConflicts            int                     `json:"maxConflicts,omitempty"`
	MinDiskFree             MinDiskFree             `json:"minDiskFree,omitempty"`
	ModTimeWindowS          int                     `json:"modTimeWindowS,omitempty"`
	Order                   string                  `json:"order,omitempty"`
	Path                    string                  `json:"path,omitempty"`
	Paused                  bool                    `json:"paused,omitempty"`
	PullerMaxPendingKiB     int                     `json:"pullerMaxPendingKiB,omitempty"`
	PullerPauseS            int                     `json:"pullerPauseS,omitempty"`
	RescanIntervalS         int                     `json:"rescanIntervalS,omitempty"`
	ScanProgressIntervalS   int                     `json:"scanProgressIntervalS,omitempty"`
	Type                    string                  `json:"type,omitempty"`
	Versioning              Versioning              `json:"versioning,omitempty"`
	WeakHashThresholdPct    int                     `json:"weakHashThresholdPct,omitempty"`
}

type SyncthingOptions struct {
	AlwaysLocalNets                     []string    `json:"alwaysLocalNets,omitempty"`
	AnnounceLANAddresses                bool        `json:"announceLANAddresses,omitempty"`
	AutoUpgradeIntervalH                int         `json:"autoUpgradeIntervalH,omitempty"`
	CacheIgnoredFiles                   bool        `json:"cacheIgnoredFiles,omitempty"`
	ConnectionLimitEnough               int         `json:"connectionLimitEnough,omitempty"`
	ConnectionLimitMax                  int         `json:"connectionLimitMax,omitempty"`
	CrURL                               string      `json:"crURL,omitempty"`
	CrashReportingEnabled               bool        `json:"crashReportingEnabled,omitempty"`
	DatabaseTuning                      string      `json:"databaseTuning,omitempty"`
	FeatureFlags                        []string    `json:"featureFlags,omitempty"`
	GlobalAnnounceEnabled               bool        `json:"globalAnnounceEnabled,omitempty"`
	GlobalAnnounceServers               []string    `json:"globalAnnounceServers,omitempty"`
	KeepTemporariesH                    int         `json:"keepTemporariesH,omitempty"`
	LimitBandwidthInLan                 bool        `json:"limitBandwidthInLan,omitempty"`
	ListenAddresses                     []string    `json:"listenAddresses,omitempty"`
	LocalAnnounceEnabled                bool        `json:"localAnnounceEnabled,omitempty"`
	LocalAnnounceMCAddr                 string      `json:"localAnnounceMCAddr,omitempty"`
	LocalAnnouncePort                   int         `json:"localAnnouncePort,omitempty"`
	MaxConcurrentIncomingRequestKiB     int         `json:"maxConcurrentIncomingRequestKiB,omitempty"`
	MaxFolderConcurrency                int         `json:"maxFolderConcurrency,omitempty"`
	MaxRecvKbps                         int         `json:"maxRecvKbps,omitempty"`
	MaxSendKbps                         int         `json:"maxSendKbps,omitempty"`
	MinHomeDiskFree                     MinDiskFree `json:"minHomeDiskFree,omitempty"`
	NatEnabled                          bool        `json:"natEnabled,omitempty"`
	NatLeaseMinutes                     int         `json:"natLeaseMinutes,omitempty"`
	NatRenewalMinutes                   int         `json:"natRenewalMinutes,omitempty"`
	NatTimeoutSeconds                   int         `json:"natTimeoutSeconds,omitempty"`
	OverwriteRemoteDeviceNamesOnConnect bool        `json:"overwriteRemoteDeviceNamesOnConnect,omitempty"`
	ProgressUpdateIntervalS             int         `json:"progressUpdateIntervalS,omitempty"`
	ReconnectionIntervalS               int         `json:"reconnectionIntervalS,omitempty"`
	RelayReconnectIntervalM             int         `json:"relayReconnectIntervalM,omitempty"`
	RelaysEnabled                       bool        `json:"relaysEnabled,omitempty"`
	ReleasesURL                         string      `json:"releasesURL,omitempty"`
	RestartOnWakeup                     bool        `json:"restartOnWakeup,omitempty"`
	SendFullIndexOnUpgrade              bool        `json:"sendFullIndexOnUpgrade,omitempty"`
	SetLowPriority                      bool        `json:"setLowPriority,omitempty"`
	StartBrowser                        bool        `json:"startBrowser,omitempty"`
	StunKeepaliveMinS                   int         `json:"stunKeepaliveMinS,omitempty"`
	StunKeepaliveStartS                 int         `json:"stunKeepaliveStartS,omitempty"`
	StunServers                         []string    `json:"stunServers,omitempty"`
	TempIndexMinBlocks                  int         `json:"tempIndexMinBlocks,omitempty"`
	TrafficClass                        int         `json:"trafficClass,omitempty"`
	UnackedNotificationIDs              []string    `json:"unackedNotificationIDs,omitempty"`
	UpgradeToPreReleases                bool        `json:"upgradeToPreReleases,omitempty"`
	UrAccepted                          int         `json:"urAccepted,omitempty"`
	UrInitialDelayS                     int         `json:"urInitialDelayS,omitempty"`
	UrPostInsecurely                    bool        `json:"urPostInsecurely,omitempty"`
	UrSeen                              int         `json:"urSeen,omitempty"`
	UrURL                               string      `json:"urURL,omitempty"`
	UrUniqueId                          string      `json:"urUniqueId,omitempty"`
}

type SyncthingConfig struct {
	Defaults             SyncthingDefaults               `json:"defaults"`
	Devices              []SyncthingDevice               `json:"devices,omitempty"`
	Folders              []SyncthingFolder               `json:"folders,omitempty"`
	Gui                  SyncthingGui                    `json:"gui"`
	Ldap                 SyncthingLdap                   `json:"ldap"`
	Options              SyncthingOptions                `json:"options"`
	RemoteIgnoredDevices []SyncthingRemoteIgnoredDevices `json:"remoteIgnoredDevices,omitempty"`
	Version              int                             `json:"version,omitempty"`
}

func NewSyncthingIgnoredFolder(id, label, time string) SyncthingIgnoredFolder {
	return SyncthingIgnoredFolder{
		ID:    id,
		Label: label,
		Time:  time,
	}
}

func NewSyncthingRemoteIgnoredDevices(address, deviceID, name, time string) SyncthingRemoteIgnoredDevices {
	return SyncthingRemoteIgnoredDevices{
		Address:  address,
		DeviceID: deviceID,
		Name:     name,
		Time:     time,
	}
}

func NewSyncthingDevice() SyncthingDevice {
	return SyncthingDevice{
		Addresses:                []string{"dynamic"},
		AllowedNetworks:          []string{},
		AutoAcceptFolders:        false,
		CertName:                 "",
		Compression:              "metadata",
		DeviceID:                 "",
		IgnoredFolders:           []SyncthingIgnoredFolder{},
		IntroducedBy:             "",
		Introducer:               false,
		MaxRecvKbps:              0,
		MaxRequestKiB:            0,
		MaxSendKbps:              0,
		Name:                     "",
		Paused:                   false,
		RemoteGUIPort:            0,
		SkipIntroductionRemovals: false,
		Untrusted:                false,
	}
}

func NewSyncthingDefaults() SyncthingDefaults {
	return SyncthingDefaults{
		Device: NewSyncthingDevice(),
		Folder: NewSyncthingFolder(),
	}
}

func NewSyncthingGui() SyncthingGui {
	return SyncthingGui{
		Debugging:           false,
		Enabled:             true,
		InsecureAdminAccess: true,
	}
}

func NewSyncthingLdap() SyncthingLdap {
	return SyncthingLdap{
		Address:            "",
		BindDN:             "",
		InsecureSkipVerify: false,
		SearchBaseDN:       "",
		SearchFilter:       "",
		Transport:          "plain",
	}
}

func NewMinDiskFree() MinDiskFree {
	return MinDiskFree{
		Unit:  "%",
		Value: 1,
	}
}

func NewVersioning() Versioning {
	return Versioning{
		CleanupIntervalS: 3600,
		FsPath:           "",
		FsType:           "basic",
		Params:           map[string]string{},
		Type:             "",
	}
}

func NewSyncthingFolderDevice(deviceID, encryptionPassword, introducedBy string) SyncthingFolderDevice {
	return SyncthingFolderDevice{
		DeviceID:           deviceID,
		EncryptionPassword: encryptionPassword,
		IntroducedBy:       introducedBy,
	}
}

func NewSyncthingFolder() SyncthingFolder {
	return SyncthingFolder{
		AutoNormalize:           true,
		BlockPullOrder:          "standard",
		CaseSensitiveFS:         false,
		Copiers:                 0,
		CopyOwnershipFromParent: false,
		CopyRangeMethod:         "standard",
		Devices:                 []SyncthingFolderDevice{},
		DisableFsync:            false,
		DisableSparseFiles:      false,
		DisableTempIndexes:      false,
		FilesystemType:          "basic",
		FsWatcherDelayS:         10,
		FsWatcherEnabled:        false,
		Hashers:                 0,
		ID:                      "",
		IgnoreDelete:            false,
		IgnorePerms:             false,
		JunctionsAsDirs:         false,
		Label:                   "",
		MarkerName:              ".stfolder",
		MaxConcurrentWrites:     2,
		MaxConflicts:            10,
		MinDiskFree:             NewMinDiskFree(),
		ModTimeWindowS:          0,
		Order:                   "random",
		Path:                    "",
		Paused:                  false,
		PullerMaxPendingKiB:     0,
		PullerPauseS:            0,
		RescanIntervalS:         3600,
		ScanProgressIntervalS:   0,
		Type:                    "sendreceive",
		Versioning:              NewVersioning(),
		WeakHashThresholdPct:    25,
	}
}

func NewSyncthingOptions() SyncthingOptions {
	return SyncthingOptions{
		AlwaysLocalNets:                     []string{},
		AnnounceLANAddresses:                false,
		AutoUpgradeIntervalH:                0,
		CacheIgnoredFiles:                   false,
		ConnectionLimitEnough:               0,
		ConnectionLimitMax:                  0,
		CrURL:                               "https://crash.syncthing.net/newcrash",
		CrashReportingEnabled:               false,
		DatabaseTuning:                      "auto",
		FeatureFlags:                        []string{},
		GlobalAnnounceEnabled:               true,
		GlobalAnnounceServers:               []string{"default"},
		KeepTemporariesH:                    24,
		LimitBandwidthInLan:                 false,
		ListenAddresses:                     []string{"default"},
		LocalAnnounceEnabled:                false,
		LocalAnnounceMCAddr:                 "[ff12::8384]:21027",
		LocalAnnouncePort:                   21027,
		MaxConcurrentIncomingRequestKiB:     0,
		MaxFolderConcurrency:                0,
		MaxRecvKbps:                         0,
		MaxSendKbps:                         0,
		MinHomeDiskFree:                     NewMinDiskFree(),
		NatEnabled:                          true,
		NatLeaseMinutes:                     60,
		NatRenewalMinutes:                   30,
		NatTimeoutSeconds:                   10,
		OverwriteRemoteDeviceNamesOnConnect: false,
		ProgressUpdateIntervalS:             5,
		ReconnectionIntervalS:               60,
		RelayReconnectIntervalM:             10,
		RelaysEnabled:                       true,
		ReleasesURL:                         "",
		RestartOnWakeup:                     true,
		SendFullIndexOnUpgrade:              false,
		SetLowPriority:                      true,
		StartBrowser:                        true,
		StunKeepaliveMinS:                   20,
		StunKeepaliveStartS:                 180,
		StunServers:                         []string{"default"},
		TempIndexMinBlocks:                  10,
		TrafficClass:                        0,
		UnackedNotificationIDs:              []string{},
		UpgradeToPreReleases:                false,
		UrAccepted:                          -1,
		UrInitialDelayS:                     1800,
		UrPostInsecurely:                    false,
		UrSeen:                              3,
		UrURL:                               "https://data.syncthing.net/newdata",
		UrUniqueId:                          "",
	}
}

func NewSyncthingConfig(
	defaults SyncthingDefaults,
	gui SyncthingGui,
	ldap SyncthingLdap,
	options SyncthingOptions,
) SyncthingConfig {
	return SyncthingConfig{
		Defaults:             defaults,
		Devices:              []SyncthingDevice{},
		Folders:              []SyncthingFolder{},
		Gui:                  gui,
		Ldap:                 ldap,
		Options:              options,
		RemoteIgnoredDevices: []SyncthingRemoteIgnoredDevices{},
		Version:              35,
	}
}
