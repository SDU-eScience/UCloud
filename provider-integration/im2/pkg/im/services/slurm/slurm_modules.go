package slurm

import (
	"encoding/json"
	"os"
	"strings"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

type LmodModuleGroup struct {
	Versions    []LmodModule        `json:"versions"`
	Package     string              `json:"package"`
	Url         util.Option[string] `json:"url"`
	Description string              `json:"description"`
	// NOTE(Dan): defaultVersionName very rarely sends "defaultVersionName": false which breaks the naive implementation
}

type LmodModule struct {
	VersionName            string     `json:"versionName"`
	CanonicalVersionString string     `json:"canonicalVersionString"`
	Path                   string     `json:"path"`
	Help                   string     `json:"help"`
	MarkedDefault          bool       `json:"markedDefault"`
	Description            string     `json:"description"`
	Full                   string     `json:"full"`
	Parent                 [][]string `json:"parent"`
}

var AvailableModules []orc.Module

func ReloadModulesFromLmod() {
	optPath := ServiceConfig.Compute.ModulesFile
	if !optPath.Present {
		return
	}

	path := optPath.Value
	data, err := os.ReadFile(path)
	if err != nil {
		log.Warn("Could not read lmod module file at '%s': %v", path, err)
		return
	}

	var groups []LmodModuleGroup
	err = json.Unmarshal(data, &groups)
	if err != nil {
		log.Warn("Unable to understand the lmod module file at '%s': %v", path, err)
		return
	}

	AvailableModules = nil
	for _, g := range groups {
		for i, v := range g.Versions {
			// NOTE(Dan): We might have multiple versions of the exact same name. This indicates that we have a choice
			// in how we load the module. Here, we simply choose to collapse that into the "DependsOn" array and keep
			// the same description for all. Technically, the descriptions can differ between them, but in practice
			// this doesn't seem to happen very often.
			//
			// Versions are sorted by the spider tool, and thus we can simply look back to see if the name matches.

			var previousVersion LmodModule
			if i > 0 {
				previousVersion = g.Versions[i-1]
			}

			if previousVersion.Full == v.Full && len(AvailableModules) > 0 {
				prevModule := &AvailableModules[len(AvailableModules)-1]
				for _, choice := range v.Parent {
					prevModule.DependsOn = append(prevModule.DependsOn, choice)
				}
			} else {
				AvailableModules = append(AvailableModules, orc.Module{
					Name:             v.Full,
					Description:      strings.TrimSpace(v.Help),
					DependsOn:        v.Parent,
					DocumentationUrl: g.Url.Value,
					ShortDescription: ensureModuleDescriptionShort(g.Description),
				})
			}
		}
	}

	log.Info("%d modules has been loaded from lmod", len(AvailableModules))
}

func ensureModuleDescriptionShort(input string) string {
	normalized := strings.TrimSpace(input)
	normalized = strings.ReplaceAll(normalized, "\n", " ")
	endOfSentence := strings.IndexRune(normalized, '.')
	if endOfSentence != -1 {
		return ensureModuleDescriptionShort(normalized[:endOfSentence])
	}

	if len(normalized) < 120 {
		return normalized
	} else {
		words := strings.Split(normalized, " ")
		builder := ""
		for i, word := range words {
			if i != 0 {
				builder += " "
			}

			currentLength := len(builder)
			if currentLength+len(word) > 120 {
				break
			}

			builder += word
		}

		return strings.TrimSpace(builder) + "..."
	}
}
