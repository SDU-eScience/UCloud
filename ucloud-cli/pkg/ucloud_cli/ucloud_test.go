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

func TestEnvironmentList(t *testing.T) {
	input := []string{"environment", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
}

func TestConnectCommand(t *testing.T) {
	input := []string{"connect", "--dev"}
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
	input := []string{"workspace", "use", "wsaf", "--dev"}
	cmd, err := Parse(input)
	assert.True(t, cmd.(*command.WorkspaceUseCommand).Dev)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}
func TestWorkspaceList(t *testing.T) {
	input := []string{"workspace", "list", "--dev"}
	cmd, err := Parse(input)
	assert.True(t, cmd.(*command.WorkspaceListCommand).Dev)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceGet(t *testing.T) {
	input := []string{"workspace", "get", "wsaf", "--dev"}
	cmd, err := Parse(input)
	assert.True(t, cmd.(*command.WorkspaceGetCommand).Dev)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceGetMissingName(t *testing.T) {
	input := []string{"workspace", "get", "--dev"}
	cmd, err := Parse(input)
	// expects error, since we are missing name
	assert.Error(t, err)
	assert.Nil(t, cmd)
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

func TestWorkplaceRename(t *testing.T) {
	input := []string{"workspace", "rename", "foo", "bar"}
	cmd, err := Parse(input)
	concrete := cmd.(*command.WorkspaceRenameCommand)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	assert.Equal(t, concrete.FromName, "foo")
	assert.Equal(t, concrete.ToName, "bar")
}

func TestEnviromentAdd(t *testing.T) {
	input := []string{"environment", "add", "foo", "bar"}
	cmd, err := Parse(input)
	concrete := cmd.(*command.EnvironmentAddCommand)
	assert.NoError(t, err)
	assert.Equal(t, concrete.Name, "foo")
	assert.Equal(t, concrete.Value, "bar")
}
