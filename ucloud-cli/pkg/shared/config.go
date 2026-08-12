package shared

type Config struct {
	CurrentWorkspace   string                 `yaml:"currentWorkspace"`
	DefaultEnvironment string                 `yaml:"defaultEnvironment"`
	Environments       map[string]Environment `yaml:"environments"`
	Workspaces         map[string]Workspace   `yaml:"workspaces"`
	Defaults           Defaults               `yaml:"defaults"`
}

type Environment struct {
	URL string `yaml:"url"`
}

type Workspace struct {
	Title       string `yaml:"title"`
	Environment string `yaml:"environment"`
	Project     string `yaml:"project"`
	Token       string `yaml:"token,omitempty"`
	TokenRef    string `yaml:"tokenRef,omitempty"`
}

type Defaults struct {
	Output       string `yaml:"output"`
	ItemsPerPage int    `yaml:"itemsPerPage"`
}
