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
	Addresses                []string                 `json:"addresses"`
	AllowedNetworks          []string                 `json:"allowedNetworks"`
	AutoAcceptFolders        bool                     `json:"autoAcceptFolders"`
	CertName                 string                   `json:"certName"`
	Compression              string                   `json:"compression"`
	DeviceID                 string                   `json:"deviceID"`
	IgnoredFolders           []SyncthingIgnoredFolder `json:"ignoredFolders"`
	IntroducedBy             string                   `json:"introducedBy"`
	Introducer               bool                     `json:"introducer"`
	MaxRecvKbps              int                      `json:"maxRecvKbps"`
	MaxRequestKiB            int                      `json:"maxRequestKiB"`
	MaxSendKbps              int                      `json:"maxSendKbps"`
	Name                     string                   `json:"name"`
	Paused                   bool                     `json:"paused"`
	RemoteGUIPort            int                      `json:"remoteGUIPort"`
	SkipIntroductionRemovals bool                     `json:"skipIntroductionRemovals"`
	Untrusted                bool                     `json:"untrusted"`
}

type SyncthingDefaults struct {
	Device SyncthingDevice `json:"device"`
	Folder SyncthingFolder `json:"folder"`
}

type SyncthingGui struct {
	Debugging           bool `json:"debugging"`
	Enabled             bool `json:"enabled"`
	InsecureAdminAccess bool `json:"insecureAdminAccess"`
}

type SyncthingLdap struct {
	Address            string `json:"address"`
	BindDN             string `json:"bindDN"`
	InsecureSkipVerify bool   `json:"insecureSkipVerify"`
	SearchBaseDN       string `json:"searchBaseDN"`
	SearchFilter       string `json:"searchFilter"`
	Transport          string `json:"transport"`
}

type MinDiskFree struct {
	Unit  string `json:"unit"`
	Value int    `json:"value"`
}

type Versioning struct {
	CleanupIntervalS int               `json:"cleanupIntervalS"`
	FsPath           string            `json:"fsPath"`
	FsType           string            `json:"fsType"`
	Params           map[string]string `json:"params"`
	Type             string            `json:"type"`
}

type SyncthingFolderDevice struct {
	DeviceID           string `json:"deviceID"`
	EncryptionPassword string `json:"encryptionPassword"`
	IntroducedBy       string `json:"introducedBy"`
}

type SyncthingFolder struct {
	AutoNormalize           bool                    `json:"autoNormalize"`
	BlockPullOrder          string                  `json:"blockPullOrder"`
	CaseSensitiveFS         bool                    `json:"caseSensitiveFS"`
	Copiers                 int                     `json:"copiers"`
	CopyOwnershipFromParent bool                    `json:"copyOwnershipFromParent"`
	CopyRangeMethod         string                  `json:"copyRangeMethod"`
	Devices                 []SyncthingFolderDevice `json:"devices"`
	DisableFsync            bool                    `json:"disableFsync"`
	DisableSparseFiles      bool                    `json:"disableSparseFiles"`
	DisableTempIndexes      bool                    `json:"disableTempIndexes"`
	FilesystemType          string                  `json:"filesystemType"`
	FsWatcherDelayS         int                     `json:"fsWatcherDelayS"`
	FsWatcherEnabled        bool                    `json:"fsWatcherEnabled"`
	Hashers                 int                     `json:"hashers"`
	ID                      string                  `json:"id"`
	IgnoreDelete            bool                    `json:"ignoreDelete"`
	IgnorePerms             bool                    `json:"ignorePerms"`
	JunctionsAsDirs         bool                    `json:"junctionsAsDirs"`
	Label                   string                  `json:"label"`
	MarkerName              string                  `json:"markerName"`
	MaxConcurrentWrites     int                     `json:"maxConcurrentWrites"`
	MaxConflicts            int                     `json:"maxConflicts"`
	MinDiskFree             MinDiskFree             `json:"minDiskFree"`
	ModTimeWindowS          int                     `json:"modTimeWindowS"`
	Order                   string                  `json:"order"`
	Path                    string                  `json:"path"`
	Paused                  bool                    `json:"paused"`
	PullerMaxPendingKiB     int                     `json:"pullerMaxPendingKiB"`
	PullerPauseS            int                     `json:"pullerPauseS"`
	RescanIntervalS         int                     `json:"rescanIntervalS"`
	ScanProgressIntervalS   int                     `json:"scanProgressIntervalS"`
	Type                    string                  `json:"type"`
	Versioning              Versioning              `json:"versioning"`
	WeakHashThresholdPct    int                     `json:"weakHashThresholdPct"`
}

type SyncthingOptions struct {
	AlwaysLocalNets                     []string    `json:"alwaysLocalNets"`
	AnnounceLANAddresses                bool        `json:"announceLANAddresses"`
	AutoUpgradeIntervalH                int         `json:"autoUpgradeIntervalH"`
	CacheIgnoredFiles                   bool        `json:"cacheIgnoredFiles"`
	ConnectionLimitEnough               int         `json:"connectionLimitEnough"`
	ConnectionLimitMax                  int         `json:"connectionLimitMax"`
	CrURL                               string      `json:"crURL"`
	CrashReportingEnabled               bool        `json:"crashReportingEnabled"`
	DatabaseTuning                      string      `json:"databaseTuning"`
	FeatureFlags                        []string    `json:"featureFlags"`
	GlobalAnnounceEnabled               bool        `json:"globalAnnounceEnabled"`
	GlobalAnnounceServers               []string    `json:"globalAnnounceServers"`
	KeepTemporariesH                    int         `json:"keepTemporariesH"`
	LimitBandwidthInLan                 bool        `json:"limitBandwidthInLan"`
	ListenAddresses                     []string    `json:"listenAddresses"`
	LocalAnnounceEnabled                bool        `json:"localAnnounceEnabled"`
	LocalAnnounceMCAddr                 string      `json:"localAnnounceMCAddr"`
	LocalAnnouncePort                   int         `json:"localAnnouncePort"`
	MaxConcurrentIncomingRequestKiB     int         `json:"maxConcurrentIncomingRequestKiB"`
	MaxFolderConcurrency                int         `json:"maxFolderConcurrency"`
	MaxRecvKbps                         int         `json:"maxRecvKbps"`
	MaxSendKbps                         int         `json:"maxSendKbps"`
	MinHomeDiskFree                     MinDiskFree `json:"minHomeDiskFree"`
	NatEnabled                          bool        `json:"natEnabled"`
	NatLeaseMinutes                     int         `json:"natLeaseMinutes"`
	NatRenewalMinutes                   int         `json:"natRenewalMinutes"`
	NatTimeoutSeconds                   int         `json:"natTimeoutSeconds"`
	OverwriteRemoteDeviceNamesOnConnect bool        `json:"overwriteRemoteDeviceNamesOnConnect"`
	ProgressUpdateIntervalS             int         `json:"progressUpdateIntervalS"`
	ReconnectionIntervalS               int         `json:"reconnectionIntervalS"`
	RelayReconnectIntervalM             int         `json:"relayReconnectIntervalM"`
	RelaysEnabled                       bool        `json:"relaysEnabled"`
	ReleasesURL                         string      `json:"releasesURL"`
	RestartOnWakeup                     bool        `json:"restartOnWakeup"`
	SendFullIndexOnUpgrade              bool        `json:"sendFullIndexOnUpgrade"`
	SetLowPriority                      bool        `json:"setLowPriority"`
	StartBrowser                        bool        `json:"startBrowser"`
	StunKeepaliveMinS                   int         `json:"stunKeepaliveMinS"`
	StunKeepaliveStartS                 int         `json:"stunKeepaliveStartS"`
	StunServers                         []string    `json:"stunServers"`
	TempIndexMinBlocks                  int         `json:"tempIndexMinBlocks"`
	TrafficClass                        int         `json:"trafficClass"`
	UnackedNotificationIDs              []string    `json:"unackedNotificationIDs"`
	UpgradeToPreReleases                bool        `json:"upgradeToPreReleases"`
	UrAccepted                          int         `json:"urAccepted"`
	UrInitialDelayS                     int         `json:"urInitialDelayS"`
	UrPostInsecurely                    bool        `json:"urPostInsecurely"`
	UrSeen                              int         `json:"urSeen"`
	UrURL                               string      `json:"urURL"`
	UrUniqueId                          string      `json:"urUniqueId"`
}

type SyncthingConfig struct {
	Defaults             SyncthingDefaults               `json:"defaults"`
	Devices              []SyncthingDevice               `json:"devices"`
	Folders              []SyncthingFolder               `json:"folders"`
	Gui                  SyncthingGui                    `json:"gui"`
	Ldap                 SyncthingLdap                   `json:"ldap"`
	Options              SyncthingOptions                `json:"options"`
	RemoteIgnoredDevices []SyncthingRemoteIgnoredDevices `json:"remoteIgnoredDevices"`
	Version              int                             `json:"version"`
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
		InsecureAdminAccess: false,
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
		AnnounceLANAddresses:                false, // Important. Always false.
		AutoUpgradeIntervalH:                0,
		CacheIgnoredFiles:                   false,
		ConnectionLimitEnough:               0,
		ConnectionLimitMax:                  0,
		CrURL:                               "https://crash.syncthing.net/newcrash",
		CrashReportingEnabled:               false, // Important. Always false.
		DatabaseTuning:                      "auto",
		FeatureFlags:                        []string{},
		GlobalAnnounceEnabled:               true,
		GlobalAnnounceServers:               []string{"default"},
		KeepTemporariesH:                    24,
		LimitBandwidthInLan:                 false,
		ListenAddresses:                     []string{ListenAddress()}, // Important.
		LocalAnnounceEnabled:                false,                     // Important. Always false.
		LocalAnnounceMCAddr:                 "[ff12::8384]:21027",
		LocalAnnouncePort:                   21027,
		MaxConcurrentIncomingRequestKiB:     0,
		MaxFolderConcurrency:                0,
		MaxRecvKbps:                         0,
		MaxSendKbps:                         0,
		MinHomeDiskFree:                     NewMinDiskFree(),
		NatEnabled:                          false, // Important. Always false?
		NatLeaseMinutes:                     60,
		NatRenewalMinutes:                   30,
		NatTimeoutSeconds:                   10,
		OverwriteRemoteDeviceNamesOnConnect: false,
		ProgressUpdateIntervalS:             5,
		ReconnectionIntervalS:               60,
		RelayReconnectIntervalM:             10,
		RelaysEnabled:                       RelaysEnabled(), // Important.
		ReleasesURL:                         "",
		RestartOnWakeup:                     true,
		SendFullIndexOnUpgrade:              false,
		SetLowPriority:                      true,
		StartBrowser:                        false, // Important. Always false.
		StunKeepaliveMinS:                   20,
		StunKeepaliveStartS:                 180,
		StunServers:                         []string{""}, // Important. Set to empty string?
		TempIndexMinBlocks:                  10,
		TrafficClass:                        0,
		UnackedNotificationIDs:              []string{},
		UpgradeToPreReleases:                false,
		UrAccepted:                          -1, // Important. Always -1 (no usage reporting).
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
