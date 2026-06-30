package ucloud_cli

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"ucloud.dk/ucloud_cli/pkg/command"
)

func TestComputeCommand(t *testing.T) {
	input := []string{"compute", "products", "--provider", "ucloud"}

	cmd, err := command.ParseCompute(input)

	assert.NoError(t, err)
	assert.NotNil(t, cmd)

}
