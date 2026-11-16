package ucloud_cli

import (
	"fmt"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/command"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

var Workspace = "test-workspace"

func TestMain(m *testing.M) {
	login()
	_, err := shared.FindWorkspaceByName(Workspace)
	if err == nil {
		fmt.Println("Test workspace already exists, skipping test setup")
	} else if err := CreateTestWorkspace(); err != nil {
		panic(err)
	}

	code := m.Run()
	os.Exit(code)
}

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
	t.Skip("Standalone test as it requires user interaction.")
	input := []string{"connect"}
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
	expectedPath := homeDir + "/.config/test_ucloud/config.yaml"
	assert.Equal(t, shared.GetConfigPath(), expectedPath)
}

func TestWorkspaceUse(t *testing.T) {
	input := []string{"workspace", "use", "test-workspace"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceCreate(t *testing.T) {
	input := []string{"workspace", "create", "test-juju"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceRename(t *testing.T) {
	input := []string{"workspace", "rename", "test-juju", "test-juju-renamed"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
	input = []string{"workspace", "rename", "test-juju-renamed", "test-juju"}
	cmd, err = Parse(input)
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
	input := []string{"workspace", "get", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestWorkspaceGetMissingName(t *testing.T) {
	input := []string{"workspace", "get"}
	_, err := Parse(input)
	// expects error, since we are missing name
	assert.Error(t, err)
}

// Add dev environment to test locally
func TestEnvironmentAddDev(t *testing.T) {
	devServerUrl := "https://ucloud.localhost.direct"
	input := []string{"environment", "add", "dev2", "--url", devServerUrl}
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

	// restore dev so the rest of the suite stays on the dev server
	input = []string{"environment", "use", "dev"}
	cmd, err = Parse(input)
	assert.NoError(t, err)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestEnvironmentAdd(t *testing.T) {
	input := []string{"environment", "add", "foo", "--url", "www.bar.com"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestEnvironmentRemove(t *testing.T) {
	input := []string{"environment", "remove", "foo"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
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

func TestJobList(t *testing.T) {
	input := []string{"job", "list", "--workspace", "test-workspace", "--provider", "k8s", "--app", "terminal-ubuntu"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobListAllFlags(t *testing.T) {
	input := []string{"job", "list", "--workspace", "test-workspace", "--state", "SUCCESS", "--app", "terminal-ubuntu", "--provider", "k8s"}
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
		"--workspace", "test-workspace",
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
	computeProduct := FindCompute()
	productStr := fmt.Sprintf("k8s/%s", computeProduct.Name)
	input := []string{
		"job", "create",
		"--app", "terminal-almalinux",
		"--product", productStr,
		"--folder", "/5/",
		"--workspace", "test-workspace",
		"--name", "test-job",
	}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobCreateCommand)
	assert.Equal(t, productStr, concrete.Product)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobCreateWithPublicLink(t *testing.T) {
	// creating the public link
	computeProduct := FindCompute()
	productStr := fmt.Sprintf("k8s/%s", computeProduct.Name)
	uuid := util.UUid()
	link := fmt.Sprintf("test-link-%s", uuid)
	input := []string{"public-link", "create", link, "--provider", "k8s"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)

	fullLink := fmt.Sprintf("k8s-%s.%s", link, "localhost.direct")
	input = []string{
		"job", "create",
		"--app", "terminal-almalinux",
		"--product", productStr,
		"--workspace", "test-workspace",
		"--public-link", fullLink,
		"--name", "test-job",
	}
	cmd, err = Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobCreateWithInvalidPublicLink(t *testing.T) {
	input := []string{
		"job", "create",
		"--app", "terminal-almalinux",
		"--product", "k8s/u1-standard-1",
		"--workspace", "test-workspace",
		"--public-link", "no-such-link-exists",
		"--name", "test-invalid-public-link-job",
	}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobCreate(t *testing.T) {
	input := []string{
		"job", "create",
		"--app", "terminal-ubuntu",
		"--workspace", "test-workspace",
		"--name", "test-job",
		"--duration", "1h30m",
		"--ssh",
	}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobCreateCommand)
	assert.Equal(t, "terminal-ubuntu", concrete.App)
	assert.Equal(t, "test-workspace", concrete.Workspace)
	assert.Equal(t, "test-job", concrete.Name)
	assert.Equal(t, "1h30m", concrete.Duration)
	assert.True(t, concrete.SSH)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobTerminate(t *testing.T) {
	// Supply the ID of a job to terminate before running this test.
	jobID := "6"
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

func TestJobResume(t *testing.T) {
	// Supply the ID of a suspended job to resume before running this test.
	jobID := "6"
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
	jobID := "6"
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
	jobID := "6"
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
	_, err := Parse(input)
	assert.Error(t, err)
}

func TestJobGetInvalidOutput(t *testing.T) {
	// Supply the ID of an existing job before running this test.
	jobID := "6"
	input := []string{"job", "get", jobID, "--output", "xml"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.Error(t, err)
}

func TestJobRename(t *testing.T) {
	// Supply the ID of a job to rename before running this test.
	jobID := "6"
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
	_, err := Parse(input)
	assert.Error(t, err)
}

func TestJobRenameMissingName(t *testing.T) {
	input := []string{"job", "rename", "6"}
	_, err := Parse(input)
	assert.Error(t, err)
}

func TestJobSearch(t *testing.T) {
	input := []string{"job", "search", "test-currently-job2", "--workspace", "test-workspace"}
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
	_, err := Parse(input)
	assert.Error(t, err)
}

func TestJobExtend(t *testing.T) {
	// Supply the ID of a running job to extend before running this test.
	jobID := "6"
	input := []string{"job", "extend", jobID, "1h"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	concrete := cmd.(*command.JobExtendCommand)
	assert.Equal(t, jobID, concrete.JobID)
	assert.Equal(t, "1h", concrete.Duration)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestJobExtendMissingID(t *testing.T) {
	input := []string{"job", "extend", "2h"}
	_, err := Parse(input)
	assert.Error(t, err)
}

func TestJobExtendMissingTime(t *testing.T) {
	input := []string{"job", "extend", "10m"}
	_, err := Parse(input)
	assert.Error(t, err)
}

func TestCompletion(t *testing.T) {
	input := []string{"completion"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestAppList(t *testing.T) {
	input := []string{"app", "list", "--category", "slurm-hpc"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestAppCategories(t *testing.T) {
	input := []string{"app", "categories"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestAppSearch(t *testing.T) {
	input := []string{"app", "search"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestAppGet(t *testing.T) {
	input := []string{"app", "get", "coder"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestComputeProducts(t *testing.T) {
	input := []string{"compute", "products", "--provider", "k8s", "--verbose"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestSshKeyList(t *testing.T) {
	input := []string{"ssh-key", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestSshKeyAdd(t *testing.T) {
	input := []string{"ssh-key", "add", "myfoobarkey", "/Users/danvu/.ssh/id_ed25519.pub"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestSshKeyGet(t *testing.T) {
	input := []string{"ssh-key", "get", "myfoobarkey"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestSshKeyDelete(t *testing.T) {
	input := []string{"ssh-key", "delete", "myfoobarkey"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPList(t *testing.T) {
	input := []string{"public-ip", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func createPublicIP(t *testing.T) string {
	input := []string{"public-ip", "create", "--rule", "1234-2345/tcp", "--rule", "12344/udp", "--workspace", Workspace}
	cmd, err := Parse(input)
	concrete := cmd.(*command.PublicIPCreateCommand)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
	return concrete.Id
}

func TestPublicIPCreate(t *testing.T) {
	createPublicIP(t)
}

func TestPublicIPListByIP(t *testing.T) {
	input := []string{"public-ip", "list", "--ip", "10.99.0.2-10.99.0.4", "--workspace", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPDelete(t *testing.T) {
	createPublicIP(t)
	input := []string{"public-ip", "delete", "--ip", "10.99.0.4-10.99.0.20", "--ip", "10.99.0.2"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPDeleteCidr(t *testing.T) {
	createPublicIP(t)
	input := []string{"public-ip", "delete", "--ip", "10.99.0.2"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPDeleteById(t *testing.T) {
	id := createPublicIP(t)
	input := []string{"public-ip", "delete", "--id", id}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPGet(t *testing.T) {
	id := createPublicIP(t)
	input := []string{"public-ip", "get", "--id", id}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPFirewall(t *testing.T) {
	id := createPublicIP(t)
	input := []string{"public-ip", "firewall", id, "--rule", "1337/tcp", "--rule", "8821-8825/udp", "--workspace", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPFirewallByIp(t *testing.T) {
	input := []string{"public-ip", "firewall", "--ip", "10.99.0.3", "--rule", "1337/tcp", "--rule", "1234-1235/udp", "--workspace", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicIPFirewallByIdAndIp(t *testing.T) {
	id := createPublicIP(t)
	input := []string{"public-ip", "firewall", id, "--ip", "10.99.0.3", "--rule", "1337/tcp", "--rule", "1234-1235/udp", "--workspace", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicLinkList(t *testing.T) {
	input := []string{"public-link", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func createPublicLink(t *testing.T, name string) string {
	input := []string{"public-link", "create", name, "--provider", "k8s"}
	cmd, err := Parse(input)
	concrete := cmd.(*command.PublicLinkCreateCommand)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
	return concrete.Id
}

func TestPublicLinkCreate(t *testing.T) {
	createPublicLink(t, "foobarlink")
}

func TestPublicLinkGet(t *testing.T) {
	createPublicLink(t, "get-test-link")
	input := []string{"public-link", "get", "get-test-link"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)

	// deleting the link to be able to rerun the test
	deletePublicLink(t, "get-test-link")
}

func deletePublicLink(t *testing.T, name string) {
	input := []string{"public-link", "delete", name, "--workspace", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPublicLinkDelete(t *testing.T) {
	deletePublicLink(t, "foobarlink")
}

func TestPrivateNetworkList(t *testing.T) {
	input := []string{"private-network", "list"}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPrivateNetworkGet(t *testing.T) {
	name := "fooprivate"
	createPrivateNetwork(t, name)
	input := []string{"private-network", "get", name}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
	deletePrivateNetwork(t, name)
}

func createPrivateNetwork(t *testing.T, name string) string {
	input := []string{"private-network", "create", name}
	cmd, err := Parse(input)
	concrete := cmd.(*command.PrivateNetworkCreateCommand)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
	return concrete.Id
}

func TestPrivateNetworkCreate(t *testing.T) {
	createPrivateNetwork(t, "mynetwork")
}

func deletePrivateNetwork(t *testing.T, name ...string) {
	input := []string{"private-network", "delete"}
	input = append(input, name...)
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
}

func TestPrivateNetworkDelete(t *testing.T) {
	deletePrivateNetwork(t, "mynetwork", "lolex")
}

func TestPrivateNetworkMembers(t *testing.T) {
	name := "my-private-network"
	createPrivateNetwork(t, name)
	input := []string{"private-network", "members", name, "--workspace", Workspace}
	cmd, err := Parse(input)
	assert.NoError(t, err)
	assert.NotNil(t, cmd)
	err = cmd.Execute()
	assert.NoError(t, err)
	deletePrivateNetwork(t, name)
}
