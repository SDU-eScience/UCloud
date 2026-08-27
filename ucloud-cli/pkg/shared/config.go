package shared

import (
	"net/http"
	"os"
	"path/filepath"
	"time"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/termio"
)

const DevServer = "https://ucloud.localhost.direct"

type Config struct {
	//Server   string `yaml:"server"`
	Username           string                 `yaml:"username"`
	TokenRef           string                 `yaml:"tokenRef,omitempty"`
	CurrentWorkspace   string                 `yaml:"currentWorkspace"`
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

	// Ensuring the config directory exists.
	if err := os.MkdirAll(GetUCloudDir(), 0700); err != nil {
		return nil, err
	}
	var cfg Config
	// Make sure the workspace map exists.
	if cfg.Environments == nil {
		cfg.DefaultEnvironment = "ucloud"
		cfg.Environments = make(map[string]Environment)
		cfg.Environments["ucloud"] = Environment{
			URL: "https://cloud.sdu.dk",
		}
	}

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

func writeFileAtomically(path string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}

	// Write to a temporary file first, then atomically replace the target file.
	tmpFile, err := os.CreateTemp(dir, filepath.Base(path)+".*.tmp")
	if err != nil {
		return err
	}
	tmpPath := tmpFile.Name()

	defer func() {
		_ = os.Remove(tmpPath)
	}()

	if _, err := tmpFile.Write(data); err != nil {
		_ = tmpFile.Close()
		return err
	}

	if err := tmpFile.Chmod(perm); err != nil {
		_ = tmpFile.Close()
		return err
	}

	if err := tmpFile.Close(); err != nil {
		return err
	}

	return os.Rename(tmpPath, path)
}

func SaveConfig(cfg *Config) (*Config, error) {
	data, err := yaml.Marshal(&cfg)
	if err != nil {
		return nil, err
	}
	return cfg, writeFileAtomically(GetConfigPath(), data, 0600)
}
func selectChange(first string, second string) string {
	if first != second && second != "" {
		return second
	}
	return first
}

func UpdateConfig(config *Config) (*Config, error) {
	cfg, err := ReadConfig()
	if err != nil {
		return nil, err
	}
	cfg.Username = selectChange(cfg.Username, config.Username)
	cfg.CurrentWorkspace = selectChange(cfg.CurrentWorkspace, config.CurrentWorkspace)
	cfg.DefaultEnvironment = selectChange(cfg.DefaultEnvironment, config.DefaultEnvironment)
	cfg.TokenRef = selectChange(cfg.TokenRef, config.TokenRef)
	return SaveConfig(cfg)
}

func (cfg *Config) initUCloudClient(dev bool) {
	baseURL := cfg.Environments[cfg.DefaultEnvironment].URL
	if dev {
		baseURL = DevServer
	}
	rpc.DefaultClient = &rpc.Client{
		RefreshToken: cfg.TokenRef,
		BasePath:     baseURL,
		Client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

func InitializeUCloudClient(dev bool) {
	cfg, err := ReadConfig()
	if err != nil {
		panic(err)
	}
	cfg.initUCloudClient(dev)
}

func PrintConfig(cfg *Config) {
	t := termio.Table{}
	t.AppendHeader("Username")
	t.AppendHeader("TokenRef")
	t.AppendHeader("CurrentWorkspace")
	t.AppendHeader("DefaultEnvironment")
	t.Cell("%v", cfg.Username)
	t.Cell("%v", cfg.TokenRef)
	t.Cell("%v", cfg.CurrentWorkspace)
	t.Cell("%v", cfg.DefaultEnvironment)
	t.Print()
}
