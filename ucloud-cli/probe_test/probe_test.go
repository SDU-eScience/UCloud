package probe_test

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"ucloud.dk/ucloud_cli/pkg/ucloud_cli"
)

const probeEnv = "dev_ucloud"

func TestMain(m *testing.M) {
	if err := selectEnv(probeEnv); err != nil {
		panic(err)
	}
	os.Exit(m.Run())
}

func selectEnv(name string) error {
	input := []string{"environment", "use", name}
	cmd, err := ucloud_cli.Parse(input)
	if err != nil {
		return err
	}
	return cmd.Execute()
}

// Use these tests to probe different environments
func TestConnectCommand(t *testing.T) {
	input := []string{"connect"}
	cmd, _ := ucloud_cli.Parse(input)
	assert.NotNil(t, cmd)
	err := cmd.Execute()
	assert.NoError(t, err)
}

func TestJobListProbe(t *testing.T) {
	input := []string{"job", "list", "--workspace", "personal"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobGetProbe(t *testing.T) {
	jobId := "3284"
	input := []string{"job", "get", jobId}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobOpenCreatorProbe(t *testing.T) {
	input := []string{"job", "open", "3350", "--provider", "k8", "--creator", "--version", "1.0"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobOpenProbe(t *testing.T) {
	input := []string{"job", "open", "3352", "--provider", "k8"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobShellProbe(t *testing.T) {
	jobId := "3352"
	input := []string{"job", "shell", jobId, "--provider", "k8", "--browser"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}
