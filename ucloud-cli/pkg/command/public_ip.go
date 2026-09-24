package command

import (
	"fmt"
	"strconv"
	"strings"

	acc "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/cli"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type PublicIPListCommand struct {
	Workspace string `flag:"workspace" usage:"Workspace to list public-ips"`
}

type PublicIPGetCommand struct {
	Name string `positional:"name" usage:"IP name"`
}
type PublicIPDeleteCommand struct {
	Name string `positional:"name" usage:"IP name"`
}

type PublicIPCreateCommand struct {
	Name      string            `positional:"name" usage:"IP name"`
	Product   string            `flag:"product" usage:"Product"`
	Parameter map[string]string `flag:"param" usage:"eg. --param start=1234 --param end=1235 --param protocol=tcp"`
	Protocol  string            `flag:"protocol" usage:"Protocol TCP/UDP"`
}

type PortRange struct {
	Start    int
	End      int
	Protocol string
}

var PublicIPCommands = map[string]CommandFunc{
	"list":   func() Command { return &PublicIPListCommand{} },
	"get":    func() Command { return &PublicIPGetCommand{} },
	"delete": func() Command { return &PublicIPDeleteCommand{} },
	"create": func() Command { return &PublicIPCreateCommand{} },
}

func printPortRangeAndProto(t *termio.Table, firewall orcapi.Firewall) {
	for _, p := range firewall.OpenPorts {
		t.Cell("%v/%v", p.Start, p.End)
	}
}

func makeHeader(t *termio.Table) {
	t.AppendHeader("Id")
	t.AppendHeader("Name")
	t.AppendHeader("Owner")
	t.AppendHeader("IpAddress")
	t.AppendHeader("State")
	t.AppendHeader("Ports")
	t.AppendHeader("CreatedAt")

}

func portsToStr(ports []orcapi.PortRangeAndProto) string {
	var s string
	for i, p := range ports {
		s += fmt.Sprintf("%v/%v", p.Start, p.End)
		if i != len(ports)-1 {
			s += ", "
		}
	}
	return s
}

/*
{"type":"bulk","items":[{"product":{"id":"public-ip","category":"public-ip","provider":"k8s"},"domain":""}]}
{"type":"bulk","items":[{"id":"42","firewall":{"openPorts":[{"start":4321,"end":4321,"protocol":"TCP"}]}}]}
*/

func printPublicIpCells(t *termio.Table, p orcapi.PublicIp) {
	t.Cell(p.Id)
	t.Cell("%v", p.ProviderGeneratedId)
	t.Cell("%v", p.Owner)
	t.Cell("%v", p.Status.IpAddress.GetOrDefault(""))
	t.Cell("%v", p.Status.State)
	t.Cell("%v", portsToStr(p.Specification.Firewall.GetOrDefault(orcapi.Firewall{}).OpenPorts))
	t.Cell("%v", cli.FormatTime(p.CreatedAt))
}

func (c PublicIPListCommand) Execute() error {
	shared.InitializeUCloudClient()
	ws, err := FindWorkspaceByName(c.Workspace)
	if err != nil {
		return err
	}
	if ws != nil {
		shared.SetActiveWorkspace(ws.Id)
	}
	result, httpErr := orcapi.PublicIpsBrowse.Invoke(orcapi.PublicIpsBrowseRequest{})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to list public ips: %s", httpErr.Why)
	}
	t := termio.Table{}
	makeHeader(&t)
	for _, ip := range result.Items {
		printPublicIpCells(&t, ip)
	}
	t.Print()
	return nil
}
func (c PublicIPGetCommand) Execute() error { return fmt.Errorf("public ip get not implemented") }
func (c PublicIPDeleteCommand) Execute() error {
	return fmt.Errorf("public ip delete not implemented")
}

func validateInput(c PublicIPCreateCommand) error {
	if c.Protocol != "TCP" && c.Protocol != "UDP" {
		return fmt.Errorf("protocol must be TCP or UDP")
	}
	return nil
}

func parsePortRange(portParams map[string]string) (PortRange, error) {
	startPort, endPort, protocol := 0, 0, ""
	for name, p := range portParams {
		if name == "start" {
			start, fail := strconv.Atoi(p)
			if fail != nil {
				return PortRange{}, fail
			}
			startPort = start
		}
		if name == "end" {
			end, fail := strconv.Atoi(p)
			if fail != nil {
				return PortRange{}, fail
			}
			endPort = end
		}
		if name == "protocol" {
			toup := strings.ToUpper(p)
			if toup != "TCP" && toup != "UDP" {
				return PortRange{}, fmt.Errorf("protocol must be TCP or UDP")
			}
			protocol = p
		}
		break
	}
	if len(portParams) > 3 {
		return PortRange{}, fmt.Errorf("too many port parameters")
	}
	if startPort != 0 && endPort != 0 && protocol != "" {
		return PortRange{
			Start:    startPort,
			End:      endPort,
			Protocol: protocol,
		}, nil
	}
	return PortRange{}, nil
}

func createPortRangeAndProto(port map[string]string) ([]orcapi.PortRangeAndProto, error) {
	portInfo, err := parsePortRange(port)
	if err != nil {
		return nil, err
	}

	var ports []orcapi.PortRangeAndProto
	var protocol orcapi.IpProtocol
	protocol = orcapi.IpProtocol(portInfo.Protocol)
	ports = append(ports, orcapi.PortRangeAndProto{
		Start:    portInfo.Start,
		End:      portInfo.End,
		Protocol: protocol,
	})
	return ports, nil
}

//ucloud public-ip create dev-ip --product ucloud/public-ip --open-port 22/tcp --open-port 443/tcp

func getProduct(productName string) {

}
func (c PublicIPCreateCommand) Execute() error {
	shared.InitializeUCloudClient()

	ports, err := createPortRangeAndProto(c.Parameter)
	if err != nil {
		return err
	}

	res, httpErr := orcapi.PublicIpsCreate.Invoke(fnd.BulkRequest[orcapi.PublicIPSpecification]{
		Items: []orcapi.PublicIPSpecification{{
			ResourceSpecification: orcapi.ResourceSpecification{
				// Fails test tomorrow ......
				Product: acc.ProductReference{
					Id:       "11",        // we need get ID from products api where the category is public-ip which is 11 from DB
					Category: "public-ip", // needs to get from command
					Provider: "UCloud",
				},
				Labels: map[string]string{},
			},
			Firewall: util.OptValue(orcapi.Firewall{
				OpenPorts: ports,
			}),
		},
		},
	})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to create public ip: %s", httpErr.Why)
	}

	fmt.Println(res)
	return nil
}
