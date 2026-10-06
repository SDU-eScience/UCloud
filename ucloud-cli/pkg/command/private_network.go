package command

import (
	"fmt"

	acc "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/cli"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type PrivateNetworkListCommand struct {
	Workspace string `flag:"workspace" usage:"eg. --workspace myworkspace"`
}

type PrivateNetworkCreateCommand struct {
	Name      string `positional:"name" usage:"Private network name" required:"true"`
	SubDomain string `positional:"sub-domain" usage:"Sub domain" required:"true"`
	Workspace string `flag:"workspace" usage:"eg. --workspace myworkspace"`
	Provider  string `flag:"provider" usage:"eg. --provider k8s" default:"k8s"`
}
type PrivateNetworkGetCommand struct {
	Name      string `positional:"name" usage:"Private network name" required:"true"`
	Workspace string `flag:"workspace" usage:"eg. --workspace myworkspace"`
}

type PrivateNetworkDeleteCommand struct {
	Name      []string `positional:"name" usage:"Private network name" required:"true"`
	Workspace string   `flag:"workspace" usage:"eg. --workspace myworkspace"`
}

type PrivateNetworkMembersCommand struct {
	Name      string `positional:"name" usage:"Private network name" required:"true"`
	Workspace string `flag:"workspace" usage:"eg. --workspace myworkspace"`
}

var PrivateNetworkCommands = map[string]CommandFunc{
	"list":    func() Command { return &PrivateNetworkListCommand{} },
	"create":  func() Command { return &PrivateNetworkCreateCommand{} },
	"get":     func() Command { return &PrivateNetworkGetCommand{} },
	"delete":  func() Command { return &PrivateNetworkDeleteCommand{} },
	"members": func() Command { return &PrivateNetworkMembersCommand{} },
}

func retrievePrivateNetworks() (map[string]orcapi.PrivateNetwork, error) {
	result, httpErr := orcapi.PrivateNetworksBrowse.Invoke(orcapi.PrivateNetworksBrowseRequest{})
	if httpErr != nil {
		return nil, httpErr
	}
	networks := make(map[string]orcapi.PrivateNetwork)
	for _, network := range result.Items {
		networks[network.Specification.Name] = network
	}
	return networks, nil
}

func printPrivateNetworks(networks map[string]orcapi.PrivateNetwork) {
	t := termio.Table{}
	t.AppendHeader("Name")
	t.AppendHeader("SubDomain")
	t.AppendHeader("CreatedAt")
	for name, network := range networks {
		t.Cell("%v", name)
		t.Cell("%v", network.Specification.Subdomain)
		t.Cell("%v", cli.FormatTime(network.CreatedAt))
	}
	t.Print()
}

func (c PrivateNetworkListCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	networks, err := retrievePrivateNetworks()
	if err != nil {
		return err
	}
	printPrivateNetworks(networks)
	return nil
}

func (c PrivateNetworkCreateCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}

	created, httpErr := orcapi.PrivateNetworksCreate.Invoke(fnd.BulkRequest[orcapi.PrivateNetworkSpecification]{
		Items: []orcapi.PrivateNetworkSpecification{
			{
				Name:      c.Name,
				Subdomain: c.SubDomain,
				ResourceSpecification: orcapi.ResourceSpecification{
					Product: acc.ProductReference{
						Id:       "private-network",
						Category: "private-network",
						Provider: c.Provider,
					},
					Labels: map[string]string{},
				},
			},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to create private network: %s", httpErr.Why)
	}
	fmt.Printf("Successfully created private network %v\n", created.Responses[0].Id)
	networks, err := retrievePrivateNetworks()
	if err != nil {
		return err
	}
	printPrivateNetworks(networks)
	return nil
}

func (c PrivateNetworkGetCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	networks, err := retrievePrivateNetworks()
	if err != nil {
		return err
	}
	network, ok := networks[c.Name]
	if !ok {
		return fmt.Errorf("private network %s not found", c.Name)
	}
	printPrivateNetworks(map[string]orcapi.PrivateNetwork{c.Name: network})
	return nil
}

func (c PrivateNetworkDeleteCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	networks, err := retrievePrivateNetworks()
	foundNetworks := map[string]fnd.FindByStringId{}
	notFoundNetworkNames := make([]string, 0)
	for _, name := range c.Name {
		network, ok := networks[name]
		if !ok {
			notFoundNetworkNames = append(notFoundNetworkNames, name)
			continue
		}
		foundNetworks[name] = fnd.FindByStringId{Id: network.Id}

	}
	if len(foundNetworks) == 0 {
		return fmt.Errorf("no private network found with name: %s", c.Name)
	}
	_, httpDeleteErr := orcapi.PrivateNetworksDelete.Invoke(fnd.BulkRequest[fnd.FindByStringId]{
		Items: util.MapValues(foundNetworks),
	})
	if httpDeleteErr.AsError() != nil {
		return fmt.Errorf("failed to delete private network: %s", httpDeleteErr.Why)
	}
	if len(notFoundNetworkNames) > 0 {
		fmt.Printf("The following private networks were not found: %v\n", notFoundNetworkNames)
	}
	fmt.Printf("Successfully deleted private network %v\n", util.MapKeys(foundNetworks))

	return nil
}

func (c PrivateNetworkMembersCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	networks, err := retrievePrivateNetworks()
	if err != nil {
		return err
	}
	network, ok := networks[c.Name]
	if !ok {
		return fmt.Errorf("private network %s not found", c.Name)
	}

	t := termio.Table{}
	t.AppendHeader("Name")
	t.AppendHeader("Members")
	for name, l := range network.Specification.Labels {
		t.Cell("%v %v", name, l)
	}
	t.Print()
	return nil
}
