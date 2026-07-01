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
	input := []string{"compute", "products"}

	cmd, err := Parse(input)

	assert.NoError(t, err)
	assert.NotNil(t, cmd)
}
