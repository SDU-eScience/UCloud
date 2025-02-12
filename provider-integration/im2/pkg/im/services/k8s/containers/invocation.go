package containers

import (
	"fmt"
	"golang.org/x/sys/unix"
	core "k8s.io/api/core/v1"
	"path/filepath"
	"strings"
	"ucloud.dk/pkg/im/services/k8s/filesystem"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func prepareInvocationOnJobCreate(
	job *orc.Job,
	rank int,
	container *core.Container,
	pathMapperInternalToPod map[string]string,
	jobFolder string,
) {
	app := &job.Status.ResolvedApplication

	invocationParameters := app.Invocation.Invocation
	parametersAndValues := orc.ReadParameterValuesFromJob(job, &app.Invocation)
	environment := app.Invocation.Environment

	ucloudToPod := func(ucloudPath string) string {
		internalPath, ok := filesystem.UCloudToInternal(ucloudPath)
		if ok {
			podPath, ok := pathMapperInternalToPod[internalPath]
			if ok {
				return podPath
			} else {
				internalPath, ok = filesystem.UCloudToInternal(util.Parent(ucloudPath))
				if ok {
					podPath, ok = pathMapperInternalToPod[internalPath]
					if ok {
						return podPath
					}
				}
			}
		}

		return "/dev/null"
	}

	argBuilder := orc.DefaultArgBuilder(ucloudToPod)

	if rank == 0 {
		var actualCommand []string
		for _, param := range invocationParameters {
			commandList := orc.BuildParameter(param, parametersAndValues, false, argBuilder, nil)
			for _, cmd := range commandList {
				actualCommand = append(actualCommand, orc.EscapeBash(cmd))
			}
		}

		path := filepath.Join(jobFolder, "job.sh")
		jobFile, ok := filesystem.OpenFile(path, unix.O_WRONLY|unix.O_CREAT, 0700)
		if ok {
			builder := strings.Builder{}
			builder.WriteString("#!/usr/bin/env bash\n")
			builder.WriteString("export TINI_SUBREAPER=\n")
			builder.WriteString("entrypoint() {\n\t")
			builder.WriteString(strings.Join(actualCommand, " "))
			builder.WriteString("\n}\n")
			builder.WriteString("entrypoint &> /work/stdout-$UCLOUD_RANK.log\n")

			_, _ = jobFile.WriteString(builder.String())
		}
		_ = jobFile.Close()
	}

	container.Command = []string{"/work/job.sh"}

	for k, param := range environment {
		commandList := orc.BuildParameter(param, parametersAndValues, false, argBuilder, nil)
		envValue := strings.Join(commandList, " ")
		container.Env = append(container.Env, core.EnvVar{
			Name:  k,
			Value: envValue,
		})
	}

	openedFile := job.Specification.OpenedFile
	if openedFile != "" {
		container.Env = append(container.Env, core.EnvVar{
			Name:  "UCLOUD_OPEN_WITH_FILE",
			Value: ucloudToPod(openedFile),
		})
	}

	container.Env = append(container.Env, core.EnvVar{
		Name:  "UCLOUD_JOB_ID",
		Value: job.Id,
	})

	replicaNames := []string{
		"UCLOUD_TASK_COUNT",
		"VC_JOB_NUM",
	}
	for _, name := range replicaNames {
		container.Env = append(container.Env, core.EnvVar{
			Name:  name,
			Value: fmt.Sprint(job.Specification.Replicas),
		})
	}

	rankNames := []string{
		"VK_TASK_INDEX",
		"VC_TASK_INDEX",
		"UCLOUD_RANK",
	}
	for _, name := range rankNames {
		container.Env = append(container.Env, core.EnvVar{
			Name:  name,
			Value: fmt.Sprint(rank),
		})
	}
}
