package ucloud_cli

import (
	"testing"

	"github.com/stretchr/testify/assert"
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
