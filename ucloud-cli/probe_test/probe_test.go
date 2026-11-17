package probe_test

import (
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/ucloud_cli/pkg/command"
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
	input := []string{"job", "open", "3350", "--creator", "--version", "1.0"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobOpenProbe(t *testing.T) {
	input := []string{"job", "open", "3352"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobShellProbe(t *testing.T) {
	jobId := "3352"
	input := []string{"job", "shell", jobId, "--browser"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestVMListProbe(t *testing.T) {
	input := []string{"vm", "list"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestVMGetProbe(t *testing.T) {
	vmId := "3727"
	input := []string{"vm", "get", vmId}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestVMCreateProbe(t *testing.T) {
	input := []string{"vm", "create", "vm-ubuntu"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)

	concrete := cmd.(*command.VMCreateCommand)
	assert.NotEmpty(t, concrete.Id)
	t.Cleanup(func() {
		terminate := []string{"job", "terminate", concrete.Id}
		termCmd, termErr := ucloud_cli.Parse(terminate)
		assert.NoError(t, termErr)
		if termCmd != nil {
			_ = termCmd.Execute()
		}
	})
}

func TestVMDeleteProbe(t *testing.T) {
	create := []string{"vm", "create", "vm-ubuntu"}
	createCmd, err := ucloud_cli.Parse(create)
	assert.NoError(t, err)
	assert.NoError(t, createCmd.Execute())
	vmId := createCmd.(*command.VMCreateCommand).Id
	assert.NotEmpty(t, vmId)

	input := []string{"vm", "delete", vmId}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestVMStopProbe(t *testing.T) {
	create := []string{"vm", "create", "vm-ubuntu"}
	createCmd, err := ucloud_cli.Parse(create)
	assert.NoError(t, err)
	assert.NoError(t, createCmd.Execute())
	vmId := createCmd.(*command.VMCreateCommand).Id
	assert.NotEmpty(t, vmId)
	t.Cleanup(func() {
		terminate := []string{"vm", "delete", vmId}
		termCmd, termErr := ucloud_cli.Parse(terminate)
		assert.NoError(t, termErr)
		if termCmd != nil {
			_ = termCmd.Execute()
		}
	})

	waitForVmState(t, vmId, orcapi.JobStateRunning, 5*time.Minute)

	input := []string{"vm", "stop", vmId}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)

	waitForVmState(t, vmId, orcapi.JobStateSuspended, 5*time.Minute)

	input = []string{"vm", "start", vmId}
	startCmd, startErr := ucloud_cli.Parse(input)
	assert.NoError(t, startErr)
	assert.NotNil(t, startCmd)
	err = startCmd.Execute()
	assert.NoError(t, err)

	waitForVmState(t, vmId, orcapi.JobStateRunning, 5*time.Minute)
}

func TestVMShellProbe(t *testing.T) {
	create := []string{"vm", "create", "vm-ubuntu"}
	createCmd, err := ucloud_cli.Parse(create)
	assert.NoError(t, err)
	assert.NoError(t, createCmd.Execute())
	vmId := createCmd.(*command.VMCreateCommand).Id
	assert.NotEmpty(t, vmId)
	t.Cleanup(func() {
		terminate := []string{"vm", "delete", vmId}
		termCmd, termErr := ucloud_cli.Parse(terminate)
		assert.NoError(t, termErr)
		if termCmd != nil {
			_ = termCmd.Execute()
		}
	})

	waitForVmState(t, vmId, orcapi.JobStateRunning, 5*time.Minute)

	input := []string{"vm", "shell", vmId, "--browser"}
	cmd, err := ucloud_cli.Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestVMCreateWithPrivateNetworkProbe(t *testing.T) {
	t.Skip("--private-network is not implemented yet")
}

func waitForVmState(t *testing.T, vmId string, state orcapi.JobState, timeout time.Duration) orcapi.Job {
	t.Helper()

	deadline := time.Now().Add(timeout)
	for {
		job, httpErr := orcapi.JobsRetrieve.Invoke(orcapi.JobsRetrieveRequest{
			Id: vmId,
		})
		if httpErr != nil {
			t.Fatalf("failed to retrieve VM %s: %s", vmId, httpErr.Why)
		}

		if job.Status.State == state {
			return job
		}
		if job.Status.State.IsFinal() {
			t.Fatalf("VM %s reached final state %s while waiting for %s", vmId, job.Status.State, state)
		}
		if time.Now().After(deadline) {
			t.Fatalf("timed out waiting for VM %s to reach %s (last state: %s)", vmId, state, job.Status.State)
		}

		time.Sleep(2 * time.Second)
	}
}
