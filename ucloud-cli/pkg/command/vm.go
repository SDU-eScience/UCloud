package command

import (
	"fmt"

	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

const supportedVMs = "vm-ubuntu"

type VMListCommand struct {
}

type VMGetCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id" required:"true"`
}

type VMDeleteCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id" required:"true"`
}

type VMStopCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id" required:"true"`
}

type VMStartCommand struct {
	VMId string `positional:"vm-id" usage:"VM Id" required:"true"`
}

type VMShellCommand struct {
	VMId    string `positional:"vm-id" usage:"VM Id" required:"true"`
	Rank    int    `flag:"rank" usage:"Rank" default:"0"`
	Browser bool   `flag:"browser" usage:"Open the shell in a browser"`
}

type VMCreateCommand struct {
	Image          string `positional:"image" usage:"Image" required:"true"`
	Product        string `flag:"product" usage:"Product"`
	SSH            bool   `flag:"ssh" usage:"Enable SSH"`
	PublicIp       string `flag:"public-ip" usage:"Public IP"`
	PublicLink     string `flag:"public-link" usage:"Public Link"`
	PrivateNetwork string `flag:"private-network" usage:"Private Network"`
	Workspace      string `flag:"workspace" usage:"Workspace"`
	Wait           bool   `flag:"wait" usage:"Wait until the VM starts running"`

	Id string
}

var VMCommands = map[string]CommandFunc{
	"list":   func() Command { return &VMListCommand{} },
	"get":    func() Command { return &VMGetCommand{} },
	"delete": func() Command { return &VMDeleteCommand{} },
	"shell":  func() Command { return &VMShellCommand{} },
	"create": func() Command { return &VMCreateCommand{} },
	"stop":   func() Command { return &VMStopCommand{} },
	"start":  func() Command { return &VMStartCommand{} },
}

func (c *VMListCommand) Execute() error {

	shared.InitializeUCloudClient()
	jobs, httpErr := retrieveJobs(orcapi.JobFlags{
		FilterApplication: util.OptStringIfNotEmpty(supportedVMs),
	})
	if httpErr != nil {
		return httpErr
	}
	return printJobs(jobs)
}

func (c *VMGetCommand) Execute() error {
	shared.InitializeUCloudClient()
	jobs, httpErr := retrieveJobs(orcapi.JobFlags{
		ResourceFlags: orcapi.ResourceFlags{
			FilterIds: util.OptStringIfNotEmpty(c.VMId),
		},
	})
	if httpErr != nil {
		return httpErr
	}
	return printJobs(jobs)
}

func (c *VMDeleteCommand) Execute() error {
	shared.InitializeUCloudClient()

	_, httpErr := orcapi.JobsTerminate.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.VMId,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to delete VM: %s", httpErr.Why)
	}

	fmt.Printf("VM deleted: %s\n", c.VMId)
	return nil
}

func (c *VMShellCommand) Execute() error {
	shared.InitializeUCloudClient()

	return shared.JobShell(c.VMId, c.Rank, c.Browser)
}

func (c *VMCreateCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()

	if c.PrivateNetwork != "" {
		return fmt.Errorf("--private-network is not implemented yet")
	}

	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}

	foundApp, appErr := findApp(c.Image)
	if appErr != nil {
		return appErr
	}
	if foundApp.Invocation.Tool.Tool.Value.Description.Backend != orcapi.ToolBackendVirtualMachine {
		return fmt.Errorf("%s is not a virtual machine image", c.Image)
	}

	var resources []orcapi.AppParameterValue
	if c.PublicIp != "" {
		publicIp, ipErr := findPublicIp(c.PublicIp)
		if ipErr != nil {
			return ipErr
		}
		if err := validatePublicIpForJob(publicIp); err != nil {
			return err
		}
		resources = append(resources, orcapi.AppParameterValue{
			Type: orcapi.AppParameterValueTypeNetwork,
			Id:   publicIp.Id,
		})
	}

	job := &JobCreateCommand{
		App:        c.Image,
		Product:    c.Product,
		SSH:        c.SSH,
		PublicLink: c.PublicLink,
		Workspace:  c.Workspace,
	}
	if err := createApp(job, foundApp, resources...); err != nil {
		return err
	}
	c.Id = job.Id
	if c.Wait {
		return waitForJobStart(c.Id)
	}
	return nil
}

func (c *VMStopCommand) Execute() error {
	shared.InitializeUCloudClient()

	_, httpErr := orcapi.JobsSuspend.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.VMId,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to stop VM: %s", httpErr.Why)
	}

	fmt.Printf("VM stopped: %s\n", c.VMId)
	return nil
}

func (c *VMStartCommand) Execute() error {
	shared.InitializeUCloudClient()

	_, httpErr := orcapi.JobsUnsuspend.Invoke(fnd.BulkRequestOf(fnd.FindByStringId{
		Id: c.VMId,
	}))
	if httpErr != nil {
		return fmt.Errorf("failed to start VM: %s", httpErr.Why)
	}

	fmt.Printf("VM started: %s\n", c.VMId)
	return nil
}
