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
	input := []string{"job", "list", "--workspace", "testmain", "--state", "SUCCESS", "--app", "terminal-ubuntuu", "--provider", "ucloud"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}
