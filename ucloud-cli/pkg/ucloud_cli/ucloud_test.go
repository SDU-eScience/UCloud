package ucloud_cli

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"ucloud.dk/ucloud_cli/pkg/command"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

func TestComputeProductCommand(t *testing.T) {
	input := []string{"compute", "products", "--provider", "ucloud"}

	cmd, err := Parse(input)

	assert.NoError(t, err)
	assert.NotNil(t, cmd)
}

func TestComputeCLI(t *testing.T) {
	input := []string{"compute", "products", "--provider"}

	cmd, err := Parse(input)

	assert.Error(t, err)
	assert.Nil(t, cmd)
}

func TestConnectCommand(t *testing.T) {
	input := []string{"connect"}
	cmd, _ := Parse(input)
	assert.NotNil(t, cmd)
	err := cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceListCommand(t *testing.T) {
	input := []string{"workspace", "list"}
	cmd, _ := Parse(input)
	assert.NotNil(t, cmd)
	err := cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceUseCommand(t *testing.T) {
	input := []string{"workspace", "use"}
	cmd, _ := Parse(input)
	assert.NotNil(t, cmd)
	err := cmd.Execute()
	assert.NoError(t, err)
}

func TestReadConfig(t *testing.T) {
	_, err := shared.ReadConfig()
	assert.NoError(t, err)
	homeDir, err := os.UserHomeDir()
	assert.NoError(t, err)
	expectedPath := homeDir + "/.config/ucloud/config.yaml"
	assert.Equal(t, shared.GetConfigPath(), expectedPath)
}

func TestWorkspaceUse(t *testing.T) {
	input := []string{"workspace", "use", "testmain"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceRename(t *testing.T) {
	input := []string{"workspace", "rename", "juju", "muju"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceRenameBack(t *testing.T) {
	input := []string{"workspace", "rename", "muju", "juju"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceList(t *testing.T) {
	input := []string{"workspace", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceGet(t *testing.T) {
	input := []string{"workspace", "get", "wsaf"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceGetMissingName(t *testing.T) {
	input := []string{"workspace", "get"}
	cmd, err := Parse(input)
	// expects error, since we are missing name
	assert.Error(t, err)
	assert.Nil(t, cmd)
}

// Add dev environment to test locally
func TestEnvironmentAddDev(t *testing.T) {
	devServerUrl := "https://ucloud.localhost.direct"
	input := []string{"environment", "add", "dev", "--url", devServerUrl}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	cmd.Execute()
	assert.NoError(t, err)
}

// Use dev environment to test locally
func TestEnvironmentUseDev(t *testing.T) {
	input := []string{"environment", "use", "dev"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestEnvironmentUse(t *testing.T) {
	input := []string{"environment", "use", "ucloud"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestEnvironmentAdd(t *testing.T) {
	input := []string{"environment", "add", "foo", "--url", "www.bar.com"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	cmd.Execute()
	assert.NoError(t, err)
}

func TestEnvironmentRemove(t *testing.T) {
	input := []string{"environment", "remove", "foo"}
	cmd, err := Parse(input)
	concrete := cmd.(*command.EnvironmentRemoveCommand)
	assert.NoError(t, err)
	assert.Equal(t, concrete.Name, "foo")
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestEnvironmentList(t *testing.T) {
	input := []string{"environment", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobCreateParams(t *testing.T) {
	input := []string{"job", "create", "--param", "image=ubuntu", "--param", "cpu=1", "--param", "memory=1024"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	assert.IsType(t, &command.JobCreateCommand{}, cmd)
	var concrete = cmd.(*command.JobCreateCommand)
	assert.NotNil(t, cmd)
	var params = map[string]string{"image": "ubuntu", "cpu": "1", "memory": "1024"}
	assert.Equal(t, concrete.Parameters, params)
}

func TestPublicIPCreateOpenPort(t *testing.T) {
	input := []string{"public-ip", "create", "--open-port", "80:80", "--open-port", "443:443"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
}

func TestPublicLinkCreate(t *testing.T) {
	input := []string{"public-link", "create", "notebook"}
	cmd, err := Parse(input)
	concrete := cmd.(*command.PublicLinkCreateCommand)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	assert.NotEmpty(t, concrete.Name)
}

func TestJobList(t *testing.T) {
	input := []string{"job", "list", "--workspace", "testmain", "--provider", "k8s"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobListAllFlags(t *testing.T) {
	input := []string{"job", "list", "--workspace", "testmain", "--state", "SUCCESS", "--app", "terminal-ubuntu", "--provider", "k8s"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobCreateWithVersion(t *testing.T) {
	input := []string{
		"job", "create",
		"--app", "terminal-almalinux:Jan2026",
		"--workspace", "testmain",
		"--name", "test-job",
	}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobCreateCommand)
	assert.Equal(t, "terminal-almalinux:Jan2026", concrete.App)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobCreateWithProviderProduct(t *testing.T) {
	input := []string{
		"job", "create",
		"--app", "terminal-almalinux",
		"--product", "k8s/u1-standard-1",
		"--folder", "/19/mysubdrive",
		"--workspace", "testmain",
		"--name", "test-currently-job",
	}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobCreateCommand)
	assert.Equal(t, "k8s/u1-standard-1", concrete.Product)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobCreate(t *testing.T) {
	input := []string{
		"job", "create",
		"--app", "terminal-ubuntu",
		"--workspace", "testmain",
		"--name", "test-job",
		"--time", "60",
		"--ssh",
	}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobCreateCommand)
	assert.Equal(t, "terminal-ubuntu", concrete.App)
	assert.Equal(t, "testmain", concrete.Workspace)
	assert.Equal(t, "test-job", concrete.Name)
	assert.Equal(t, 60, concrete.Time)
	assert.True(t, concrete.SSH)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobTerminate(t *testing.T) {
	// Supply the ID of a job to terminate before running this test.
	jobID := "10"
	input := []string{"job", "terminate", jobID}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobTerminateCommand)
	assert.Equal(t, jobID, concrete.JobID)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobTerminateMissingID(t *testing.T) {
	input := []string{"job", "terminate"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobDeleteMissingID(t *testing.T) {
	input := []string{"job", "delete"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobResume(t *testing.T) {
	// Supply the ID of a suspended job to resume before running this test.
	jobID := "16"
	input := []string{"job", "resume", jobID}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobResumeCommand)
	assert.Equal(t, jobID, concrete.JobID)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobResumeMissingID(t *testing.T) {
	input := []string{"job", "resume"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobGet(t *testing.T) {
	// Supply the ID of a job to retrieve before running this test.
	jobID := "13"
	input := []string{"job", "get", jobID}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobGetCommand)
	assert.Equal(t, jobID, concrete.JobID)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobGetJSON(t *testing.T) {
	// Supply the ID of a job to retrieve before running this test.
	jobID := "13"
	input := []string{"job", "get", jobID, "--output", "json"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobGetCommand)
	assert.Equal(t, jobID, concrete.JobID)
	assert.Equal(t, "json", concrete.Output)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobGetMissingID(t *testing.T) {
	input := []string{"job", "get"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobGetInvalidOutput(t *testing.T) {
	// Supply the ID of an existing job before running this test.
	jobID := "10"
	input := []string{"job", "get", jobID, "--output", "xml"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobRename(t *testing.T) {
	// Supply the ID of a job to rename before running this test.
	jobID := "13"
	newName := "renamed-job"
	input := []string{"job", "rename", jobID, newName}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobRenameCommand)
	assert.Equal(t, jobID, concrete.JobID)
	assert.Equal(t, newName, concrete.NewName)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobRenameMissingID(t *testing.T) {
	input := []string{"job", "rename", "new-name"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobRenameMissingName(t *testing.T) {
	input := []string{"job", "rename", "13"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobSearch(t *testing.T) {
	input := []string{"job", "search", "test-currently-job2", "--workspace", "testmain"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobSearchCommand)
	assert.Equal(t, "test-currently-job2", concrete.JobName)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobSearchMissingName(t *testing.T) {
	input := []string{"job", "search"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobExtend(t *testing.T) {
	// Supply the ID of a running job to extend before running this test.
	jobID := "14"
	input := []string{"job", "extend", jobID, "--time", "60"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobExtendCommand)
	assert.Equal(t, jobID, concrete.JobID)
	assert.Equal(t, 60, concrete.Time)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobExtendMissingID(t *testing.T) {
	input := []string{"job", "extend", "--time", "60"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobExtendMissingTime(t *testing.T) {
	input := []string{"job", "extend", "10"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}
