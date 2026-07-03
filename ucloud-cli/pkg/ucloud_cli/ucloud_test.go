package ucloud_cli

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"ucloud.dk/ucloud_cli/pkg/command"
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
