package shared

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/rpc"
)

const DEV_SERVER = "https://ucloud.localhost.direct"

type Config struct {
	//Server   string `yaml:"server"`
	Username string `yaml:"username"`
	TokenRef string `yaml:"tokenRef,omitempty"`
	//CurrentWorkspace   string                 `yaml:"currentWorkspace"`
	DefaultEnvironment string                 `yaml:"defaultEnvironment"`
	Environments       map[string]Environment `yaml:"environments"`
	//Workspaces         map[string]Workspace   `yaml:"workspaces"`
	Defaults Defaults `yaml:"defaults"`
}

type Environment struct {
	URL string `yaml:"url"`
}

type Workspace struct {
}

type Defaults struct {
	Output       string `yaml:"output"`
	ItemsPerPage int    `yaml:"itemsPerPage"`
}

func GetUCloudDir() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "ucloud")
}

func GetConfigPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "ucloud", "config.yaml")
}

func ReadConfig() (*Config, error) {

	// Make sure ~/.config/ucloud exists.
	if err := os.MkdirAll(GetUCloudDir(), 0700); err != nil {
		return nil, err
	}

	var cfg Config

	// Make sure the workspace map exists.

	if cfg.Environments == nil {
		cfg.Environments = make(map[string]Environment)
		cfg.Environments["ucloud"] = Environment{
			URL: "https://cloud.sdu.dk",
		}
	}

	//cfg.DefaultEnvironment = "ucloud"
	cfg.Defaults = Defaults{
		Output:       "table",
		ItemsPerPage: 100,
	}

	data, err := os.ReadFile(GetConfigPath())
	if err != nil {
		if !os.IsNotExist(err) {
			return nil, err
		}
		// Config doesn't exist yet, create an empty one.
		data = []byte{}
	}
	// Empty config is valid.
	if len(data) > 0 {
		if err := yaml.Unmarshal(data, &cfg); err != nil {
			return nil, err
		}
	}
	return &cfg, nil
}

func SaveConfig(cfg *Config) error {
	data, err := yaml.Marshal(&cfg)
	if err != nil {
		return err
	}
	fmt.Println("Saving config to:", GetConfigPath())

	return os.WriteFile(GetConfigPath(), data, 0600)
}

func (cfg *Config) InitUCloudClient(dev bool) {
	baseURL := cfg.Environments[cfg.DefaultEnvironment].URL
	if dev {
		baseURL = DEV_SERVER
	}
	rpc.DefaultClient = &rpc.Client{
		RefreshToken: cfg.TokenRef,
		BasePath:     baseURL,
		Client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}
