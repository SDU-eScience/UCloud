package main

import (
	"encoding/json"
	"encoding/xml"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"time"
)

type UCloudSyncthingConfigFolder struct {
	ID string `json:"id"`
}

func (s *UCloudSyncthingConfigFolder) Path() string {
	return os.Getenv("f" + s.ID)
}

type UCloudSyncthingConfigDevice struct {
	DeviceID string `json:"deviceId"`
	Label    string `json:"label"`
}

type UCloudSyncthingConfig struct {
	Folders []UCloudSyncthingConfigFolder `json:"folders"`
	Devices []UCloudSyncthingConfigDevice `json:"devices"`
}

type UCloudConfigService struct {
	configFolder           string
	apiKey                 string
	deviceID               string
	config                 UCloudSyncthingConfig
	syncthingClient        *SyncthingClient
	previouslyObservedFile string
	logger                 *log.Logger
	xmlConfig              SyncthingConfigXML
}

func NewUCloudConfigService(configFolder string) (*UCloudConfigService, error) {
	service := &UCloudConfigService{
		configFolder: configFolder,
		logger:       log.New(os.Stdout, "UCloudConfigService: ", log.LstdFlags),
	}

	return service, nil
}

func (s *UCloudConfigService) Run() error {
	syncthingConfigPath := filepath.Join(s.configFolder, "config.xml")

	timeout := time.Now().Add(10 * time.Second)
	for time.Now().Before(timeout) {
		if _, err := os.Stat(syncthingConfigPath); err == nil {
			break
		}
		time.Sleep(500 * time.Millisecond)
	}

	configBytes, err := os.ReadFile(syncthingConfigPath)
	if err != nil {
		return fmt.Errorf("failed to read Syncthing config: %v", err)
	}

	if err := xml.Unmarshal(configBytes, &s.xmlConfig); err != nil {
		return fmt.Errorf("failed to parse Syncthing config: %v", err)
	}

	s.apiKey = s.xmlConfig.GUI.APIKey
	if len(s.xmlConfig.Device) > 0 {
		s.deviceID = s.xmlConfig.Device[0].ID
	}

	s.syncthingClient = NewSyncthingClient(s.apiKey)

	deviceIDFile := filepath.Join(s.configFolder, "ucloud_device_id.txt")
	if _, err := os.Stat(deviceIDFile); os.IsNotExist(err) {
		os.WriteFile(deviceIDFile, []byte(s.deviceID), 0644)
	}

	ucloudConfigFile := filepath.Join(s.configFolder, "ucloud_config.json")
	s.processConfigChanges(ucloudConfigFile)

	for {
		time.Sleep(500 * time.Millisecond)
	}
}

func (s *UCloudConfigService) processConfigChanges(configPath string) {
	configBytes, err := os.ReadFile(configPath)
	if err != nil {
		s.logger.Printf("Unable to read config: %v", err)
		return
	}

	var newConfig UCloudSyncthingConfig
	if err := json.Unmarshal(configBytes, &newConfig); err != nil {
		s.logger.Printf("Unable to parse config: %v", err)
		return
	}

	s.config = s.validate(newConfig)

	RetryOrPanic[Empty]("rename default syncthing device", func() (Empty, error) {
		for _, configuredDevice := range s.xmlConfig.Device {
			if configuredDevice.ID == s.deviceID && configuredDevice.Name != "UCloud" {
				err := s.syncthingClient.AddDevices([]UCloudSyncthingConfigDevice{
					{
						DeviceID: s.deviceID,
						Label:    "UCloud",
					},
				})

				return Empty{}, err
			}
		}

		return Empty{}, nil
	})

	RetryOrPanic[Empty]("configure default options and GUI", func() (Empty, error) {
		err := s.syncthingClient.ConfigureOptions(NewSyncthingOptions())
		if err != nil {
			return Empty{}, err
		}

		err = s.syncthingClient.ConfigureGui(NewSyncthingGui())
		return Empty{}, err
	})

	RetryOrPanic[Empty]("remove unknown devices", func() (Empty, error) {
		var devicesToRemove []string

		for _, configuredDevice := range s.xmlConfig.Device {
			found := false
			if configuredDevice.ID == s.deviceID {
				found = true
			} else {
				for _, expectedDevice := range s.config.Devices {
					if configuredDevice.ID == expectedDevice.DeviceID {
						found = true
						break
					}
				}
			}

			if !found {
				devicesToRemove = append(devicesToRemove, configuredDevice.ID)
			}
		}

		err := s.syncthingClient.RemoveDevices(devicesToRemove)
		return Empty{}, err
	})

	RetryOrPanic[Empty]("add new devices", func() (Empty, error) {
		var devicesToAdd []UCloudSyncthingConfigDevice

		for _, expectedDevice := range s.config.Devices {
			found := false

			for _, configuredDevice := range s.xmlConfig.Device {
				if expectedDevice.DeviceID == configuredDevice.ID {
					found = true
					break
				}
			}

			if !found {
				devicesToAdd = append(devicesToAdd, expectedDevice)
			}
		}

		err := s.syncthingClient.AddDevices(devicesToAdd)
		return Empty{}, err
	})

	RetryOrPanic[Empty]("remove unknown folders", func() (Empty, error) {
		var foldersToRemove []UCloudSyncthingConfigFolder

		for _, configuredFolder := range s.xmlConfig.Folder {
			found := false
			for _, expectedFolder := range s.config.Folders {
				if expectedFolder.ID == configuredFolder.ID {
					found = true
					break
				}
			}

			if !found {
				foldersToRemove = append(foldersToRemove, UCloudSyncthingConfigFolder{ID: configuredFolder.ID})
			}
		}

		err := s.syncthingClient.RemoveFolders(foldersToRemove)
		return Empty{}, err
	})

	RetryOrPanic[Empty]("add new folders", func() (Empty, error) {
		err := s.syncthingClient.AddFolders(s.config.Folders, s.config.Devices)
		return Empty{}, err
	})
}

func (s *UCloudConfigService) validate(newConfig UCloudSyncthingConfig) UCloudSyncthingConfig {
	validatedFolders := []UCloudSyncthingConfigFolder{}

	for _, folder := range newConfig.Folders {
		if _, err := os.Stat(folder.Path()); err == nil {
			validatedFolders = append(validatedFolders, folder)
		}
	}

	return UCloudSyncthingConfig{
		Folders: validatedFolders,
		Devices: newConfig.Devices,
	}
}
