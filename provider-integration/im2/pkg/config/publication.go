package config

import (
	"fmt"
	"net/url"
	"os"
	"strings"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/cfgutil"
)

type PublicationTargetKind string

const (
	PublicationTargetKindPure PublicationTargetKind = "pure"
)

var PublicationTargetKindOptions = []PublicationTargetKind{
	PublicationTargetKindPure,
}

type PublicationConfiguration struct {
	Enabled       bool
	Project       string   // projectId of the project responsible for holding the archived data
	AllowedOrgIds []string // organizations allowed to use publication feature
	Target        PublicationTarget
}

func (c *PublicationConfiguration) Supported() bool {
	return c != nil && c.Enabled
}

type PublicationTarget struct {
	Kind          PublicationTargetKind
	Configuration any
}

func (t *PublicationTarget) Pure() *PublicationTargetPure {
	if t.Kind == PublicationTargetKindPure {
		return t.Configuration.(*PublicationTargetPure)
	}
	return nil
}

type PublicationTargetPure struct {
	ApiServer  string
	ApiKeyFile string
}

// LoadApiKey reads and returns the API key stored in the file pointed to by apiKeyFile.
func (t *PublicationTargetPure) LoadApiKey() (string, error) {
	data, err := os.ReadFile(t.ApiKeyFile)
	if err != nil {
		return "", fmt.Errorf("failed to read the Pure API key from %v: %w", t.ApiKeyFile, err)
	}

	key := strings.TrimSpace(string(data))
	if key == "" {
		return "", fmt.Errorf("the Pure API key file %v is empty", t.ApiKeyFile)
	}

	return key, nil
}

func parsePublication(filePath string, node *yaml.Node) (bool, PublicationConfiguration) {
	cfg := PublicationConfiguration{}
	success := true

	cfg.Enabled = cfgutil.RequireChildBool(filePath, node, "enabled", &success)
	if !cfg.Enabled {
		return true, cfg
	}

	cfg.Project = cfgutil.RequireChildText(filePath, node, "project", &success)

	allowedOrgIdsNode := cfgutil.RequireChild(filePath, node, "allowedOrgIds", &success)
	if allowedOrgIdsNode != nil {
		cfgutil.Decode(filePath, allowedOrgIdsNode, &cfg.AllowedOrgIds, &success)
	}

	if len(cfg.AllowedOrgIds) == 0 {
		cfgutil.ReportError(filePath, allowedOrgIdsNode,
			"allowedOrgIds must contain at least one organization when publication is enabled")
		success = false
	}

	targetNode := cfgutil.RequireChild(filePath, node, "target", &success)
	cfg.Target.Kind = cfgutil.RequireChildEnum(filePath, targetNode, "kind", PublicationTargetKindOptions, &success)

	switch cfg.Target.Kind {
	case PublicationTargetKindPure:
		pure := PublicationTargetPure{}

		pure.ApiServer = cfgutil.RequireChildText(filePath, targetNode, "apiServer", &success)

		// The API key file only needs to be readable by the IM server instance.
		if Mode == ServerModeServer {
			pure.ApiKeyFile = cfgutil.RequireChildFile(filePath, targetNode, "apiKeyFile", cfgutil.FileCheckRead, &success)
		} else {
			pure.ApiKeyFile = cfgutil.OptionalChildText(filePath, targetNode, "apiKeyFile", &success)
		}

		if pure.ApiServer != "" {
			parsed, err := url.Parse(pure.ApiServer)
			if err != nil || parsed.Scheme == "" || parsed.Host == "" {
				cfgutil.ReportError(filePath, targetNode,
					"apiServer must be a valid absolute URL, for example https://pure.example.org/ws/api")
				success = false
			}
		}

		cfg.Target.Configuration = &pure
	}

	return success, cfg
}
