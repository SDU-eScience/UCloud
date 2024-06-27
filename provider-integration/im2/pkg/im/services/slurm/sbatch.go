package slurm

import (
	"fmt"
	"math/rand"
	"net/http"
	"path/filepath"
	"strings"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

func CreateSBatchFile(job *orc.Job, jobFolder string) (string, error) {
	application := &job.Status.ResolvedApplication.Invocation
	tool := &job.Status.ResolvedApplication.Invocation.Tool.Tool

	machineConfig, ok := ServiceConfig.Compute.Machines[job.Specification.Product.Category]
	if !ok {
		return "", &util.HttpError{
			StatusCode: http.StatusInternalServerError,
			Why:        "Unknown product requested",
		}
	}

	accountName := ""
	{
		accounts := AccountMapper.UCloudConfigurationFindSlurmAccount(SlurmJobConfiguration{
			Owner:              orc.ResourceOwnerToWalletOwner(job.Resource),
			EstimatedProduct:   job.Specification.Product,
			EstimatedNodeCount: job.Specification.Replicas,
		})

		if len(accounts) != 1 {
			return "", &util.HttpError{
				StatusCode: http.StatusInternalServerError,
				Why:        "Ambiguous number of accounts",
			}
		}

		accountName = accounts[0]
	}

	formattedTimeAllocation := ""
	{
		var timeAllocation orc.SimpleDuration
		if job.Specification.TimeAllocation.IsSet() {
			timeAllocation = job.Specification.TimeAllocation.Get()
		} else {
			timeAllocation = tool.Description.DefaultTimeAllocation
		}

		formattedTimeAllocation += formatTimeNumber(timeAllocation.Hours)
		formattedTimeAllocation += ":"
		formattedTimeAllocation += formatTimeNumber(timeAllocation.Minutes)
		formattedTimeAllocation += ":"
		formattedTimeAllocation += formatTimeNumber(timeAllocation.Seconds)
	}

	parametersAndValues := orc.ReadParameterValuesFromJob(job, application)

	argBuilder := orc.DefaultArgBuilder(func(ucloudPath string) string {
		internalPath, _ := UCloudToInternal(ucloudPath)
		return internalPath
	})

	var cliInvocation []string
	{
		for _, invParam := range application.Invocation {
			newCliArgs := orc.BuildParameter(invParam, parametersAndValues, false, argBuilder)
			for _, cliArg := range newCliArgs {
				cliInvocation = append(cliInvocation, orc.EscapeBash(cliArg))
			}
		}
	}

	memoryAllocation := ""
	{
		allocInGigs := job.Status.ResolvedProduct.MemoryInGigs
		if allocInGigs <= 0 {
			allocInGigs = 1
		}

		memoryAllocation = fmt.Sprintf("%d", allocInGigs*1000)
	}

	cpuAllocation := job.Status.ResolvedProduct.Cpu
	{
		if cpuAllocation <= 0 {
			cpuAllocation = 1
		}
	}

	builder := &strings.Builder{}
	{
		appendLine(builder, "#!/usr/bin/env bash")
		appendLine(builder, "#SBATCH --chdir %v", orc.EscapeBash(jobFolder))
		appendLine(builder, "#SBATCH --cpus-per-task %d", cpuAllocation)
		appendLine(builder, "#SBATCH --mem %v", memoryAllocation)
		appendLine(builder, "#SBATCH --gpus-per-node %d", job.Status.ResolvedProduct.Gpu)
		appendLine(builder, "#SBATCH --time %v", formattedTimeAllocation)
		appendLine(builder, "#SBATCH --nodes %d", job.Specification.Replicas)
		appendLine(builder, "#SBATCH --job-name %v", job.Id)
		appendLine(builder, "#SBATCH --parsable")
		appendLine(builder, "#SBATCH --output=stdout.txt")
		appendLine(builder, "#SBATCH --error=stderr.txt")
		appendLine(builder, "#SBATCH --partition %v", machineConfig.Partition)
		if machineConfig.Qos.IsSet() {
			appendLine(builder, "#SBATCH --qos %v", machineConfig.Qos.Get())
		}
		appendLine(builder, "#SBATCH --account %v", accountName)
		appendLine(builder, "")

		if application.ApplicationType == orc.ApplicationTypeWeb || application.ApplicationType == orc.ApplicationTypeVnc {
			allocatedPort := 10000 + rand.Intn(40000)
			appendLine(builder, "export UCLOUD_PORT=%d", allocatedPort)
			appendLine(builder, "echo %d > %v", allocatedPort, orc.EscapeBash(filepath.Join(jobFolder, AllocatedPortFile)))
			appendLine(builder, "")
		}

		if len(application.Environment) > 0 {
			for key, param := range application.Environment {
				args := orc.BuildParameter(param, parametersAndValues, true, argBuilder)
				value := orc.EscapeBash(strings.Join(args, " "))
				appendLine(builder, "export %v=%v", key, value)
			}
			appendLine(builder, "")
		}

		if len(tool.Description.RequiredModules) > 0 {
			appendLine(builder, "module purge")
			for _, module := range tool.Description.RequiredModules {
				appendLine(builder, "module load '%v'", module)
			}
			appendLine(builder, "")
		}

		// TODO constraints

		// Run the actual program
		// ---------------------------------------------------------
		appendLine(builder, strings.Join(cliInvocation, " "))
	}

	return builder.String(), nil
}

const AllocatedPortFile = "allocated-port.txt"

func appendLine(builder *strings.Builder, formatString string, args ...any) {
	builder.WriteString(fmt.Sprintf(formatString+"\n", args...))
}

func formatTimeNumber(value int) string {
	if value < 10 {
		return "0" + fmt.Sprint(value)
	} else {
		return fmt.Sprint(value)
	}
}
