package containers

import (
	"encoding/json"
	"fmt"
	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	"os"
	"path/filepath"
	"strings"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/termio"
	"ucloud.dk/pkg/util"
)

var nixModules = map[string][]orc.Module{}

func LoadNixModules() {
	entries, err := os.ReadDir("/etc/ucloud")
	if err == nil {
		for _, entry := range entries {
			name := entry.Name()
			if strings.HasPrefix(name, "nix-") && strings.HasSuffix(name, ".json") {
				name, _ = strings.CutPrefix(name, "nix-")
				name, _ = strings.CutSuffix(name, ".json")

				var modules []orc.Module
				data, _ := os.ReadFile(filepath.Join("/etc/ucloud", entry.Name()))
				err = json.Unmarshal(data, &modules)
				if err == nil {
					nixModules[name] = modules
					log.Info("nix %v %v modules", name, len(modules))
				}
			}
		}
	}
}

func ParseNixVersion(app *orc.Application) (string, bool) {
	for _, param := range app.Invocation.Parameters {
		if param.Type == orc.ApplicationParameterTypeWorkflow {
			var spec orc.WorkflowSpecification
			err := json.Unmarshal(param.DefaultValue, &spec)
			if err == nil {
				i := strings.Index(spec.Job.Value, "# nix: ")
				if i != -1 {
					substring := spec.Job.Value[i+7:]
					endIndex := strings.Index(substring, "\n")
					if endIndex == -1 {
						endIndex = len(substring)
					}

					version := substring[:endIndex]
					return version, true
				}
			}
			break
		}
	}
	return "", false
}

const InjectedPrefix = "_injected_"
const NixParameter = InjectedPrefix + "nixpkgs"

func RequestNixParameter(app *orc.Application) []orc.ApplicationParameter {
	nixVersion, _ := ParseNixVersion(app)
	modules, ok := nixModules[nixVersion]
	if !ok {
		return nil
	}

	return []orc.ApplicationParameter{
		orc.ApplicationParameterModuleList(
			NixParameter,
			"Modules",                 // TODO better description
			"List of modules to load", // TODO better description
			modules,
		),
	}
}

func generateNixEntrypoint(
	job *orc.Job,
	rank int,
	pod *core.Pod,
	container *core.Container,
	parametersAndValues map[string]orc.ParamAndValue,
	jobFolder string,
	oldCommand []string,
) entrypointExtension {
	pv, ok := parametersAndValues[NixParameter]
	if !ok {
		return entrypointExtension{}
	}

	memberfiles, _, err := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, util.OptStringIfNotEmpty(job.Owner.Project))
	if err != nil {
		return entrypointExtension{}
	}

	nixPath := filepath.Join(memberfiles, ".nixpkgs")
	err = filesystem.DoCreateFolder(nixPath)
	if err != nil {
		return entrypointExtension{}
	}

	subpath, ok := strings.CutPrefix(nixPath, filepath.Clean(ServiceConfig.FileSystem.MountPoint)+"/")
	if !ok {
		return entrypointExtension{}
	}

	if rank == 0 {
		shellFile, ok := filesystem.OpenFile(filepath.Join(jobFolder, "shell.nix"), unix.O_WRONLY|unix.O_CREAT, 0660)
		if !ok {
			return entrypointExtension{}
		}

		toImport := strings.Builder{}
		for _, module := range pv.Value.Modules {
			toImport.WriteString(" pkgs.")
			toImport.WriteString(module)
		}

		_, _ = shellFile.WriteString(fmt.Sprintf(`{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = [%s ];
  shellHook = ''
    export PS1="\[\e[1;34m\](nix-shell) \w \$\[\e[0m\] "
  '';
}`, toImport.String()))

		err = shellFile.Close()
		if err != nil {
			return entrypointExtension{}
		}
	}

	newScriptName := fmt.Sprintf("job-nix-%d.sh", rank)
	nixJobFile, ok := filesystem.OpenFile(filepath.Join(jobFolder, newScriptName), unix.O_WRONLY|unix.O_CREAT|unix.O_TRUNC, 0660)
	if !ok {
		return entrypointExtension{}
	}

	nixJob := strings.Builder{}
	nixJob.WriteString("#!/usr/bin/env bash\n")
	if rank == 0 {
		nixJob.WriteString("rsync -rah /nix-base/* /nix/\n")
		nixJob.WriteString("touch /work/.nix-ready\n")
	} else {
		nixJob.WriteString("while [ ! -f \"/work/.nix-ready\" ]; do\n")
		nixJob.WriteString("\tsleep 0.5\n")
		nixJob.WriteString("done\n")
	}

	nixJob.WriteString(fmt.Sprintf("nix-shell --run %s shell.nix\n", orc.EscapeBash(strings.Join(oldCommand, " "))))
	_, _ = nixJobFile.WriteString(nixJob.String())
	_ = nixJobFile.Chmod(0777)
	err = nixJobFile.Close()
	if err != nil {
		return entrypointExtension{}
	}

	// TODO(Dan): I suspect that the only chance this might work is if we only mount /nix/store and keep /nix/var per
	//   node. I might be wrong and this might not even be possible at all in this configuration. I don't really know
	//   yet.
	container.VolumeMounts = append(container.VolumeMounts, core.VolumeMount{
		Name:      "ucloud-filesystem",
		MountPath: "/nix",
		SubPath:   subpath,
	})

	return entrypointExtension{
		Valid:      true,
		NewCommand: []string{filepath.Join("/work", newScriptName)},
	}
}

func HandleNixProcessCli() {
	var ourArgs []string
	if len(os.Args) >= 3 {
		ourArgs = os.Args[2:]
	}

	targetFile := util.GetOptionalElement(ourArgs, 0)
	outputFile := util.GetOptionalElement(ourArgs, 1)

	if !targetFile.Present || !outputFile.Present {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Usage: ucloud nix-process <targetFile> <outputFile>")
		os.Exit(1)
	}

	type nixEntry struct {
		Name    string              `json:"name"`
		Version util.Option[string] `json:"version"`
	}

	var nixInput map[string]nixEntry

	data, _ := os.ReadFile(targetFile.Value)
	err := json.Unmarshal(data, &nixInput)
	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Could not read/parse targetFile: %s", err)
		os.Exit(1)
	}

	var output []orc.Module
	for name, entry := range nixInput {
		cleanName, _ := strings.CutPrefix(name, "nixpkgs.")
		output = append(output, orc.Module{
			Name:        cleanName,
			Description: entry.Version.Value,
		})
	}

	data, err = json.Marshal(output)
	if err == nil {
		err = os.WriteFile(outputFile.Value, data, 0660)
	}

	if err != nil {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Could not write outputFile: %s", err)
		os.Exit(1)
	}
}
