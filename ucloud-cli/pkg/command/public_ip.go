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
	Workspace string   `flag:"workspace" usage:"Workspace to list public-ips"`
	IPAddress []string `flag:"ip" usage:"eg. --ip 10.99.0.7-10.99.0.8 --ip 10.99.0.0/24"`
}

type PublicIPGetCommand struct {
	//using id for now, since named public-ip is not supported yet
	Id        []string `flag:"id" usage:"eg. --id 15 --id 13" required:"true"`
	Workspace string   `flag:"workspace" usage:"eg. --workspace myworkspace"`
}

type PublicIPDeleteCommand struct {
	Ids       []string `flag:"id" usage:"--id 15 --id 16"`
	IPAddress []string `flag:"ip" usage:"10.99.0.2 10.99.0.7-10.99.0.8"`
	Workspace string   `flag:"workspace" usage:"eg. --workspace myworkspace"`
}

type PublicIPCreateCommand struct {
	// TODO: add named public-ip, when it is supported by the API
	Rule      []string `flag:"rule" usage:"eg. 1234-2345/tcp" required:"true"`
	Workspace string   `flag:"workspace" usage:"Workspace to create the public-ip in"`
	Provider  string   `flag:"provider" usage:"Provider name" default:"k8s"`
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

func makeHeader(t *termio.Table) {
	t.AppendHeader("Id")
	t.AppendHeader("Owner")
	t.AppendHeader("Workspace")
	t.AppendHeader("IpAddress")
	t.AppendHeader("State")
	t.AppendHeader("Ports")
	t.AppendHeader("CreatedAt")

}

func portsToStr(ports []orcapi.PortRangeAndProto) string {
	var s string
	for i, p := range ports {
		s += fmt.Sprintf("%v-%v/%v", p.Start, p.End, p.Protocol)
		if i != len(ports)-1 {
			s += ", "
		}
	}
	return s
}

func printPublicIpCells(t *termio.Table, workspaceName string, p orcapi.PublicIp) {
	t.Cell(p.Id)
	t.Cell("%v", p.Owner.CreatedBy)
	t.Cell("%v", workspaceName)
	t.Cell("%v", p.Status.IpAddress.GetOrDefault(""))
	t.Cell("%v", p.Status.State)
	t.Cell("%v", portsToStr(p.Specification.Firewall.GetOrDefault(orcapi.Firewall{}).OpenPorts))
	t.Cell("%v", cli.FormatTime(p.CreatedAt))
}
func retrievePublicIps() ([]orcapi.PublicIp, error) {
	result, httpErr := orcapi.PublicIpsBrowse.Invoke(orcapi.PublicIpsBrowseRequest{})
	if httpErr.AsError() != nil {
		return nil, fmt.Errorf("failed to list public ips: %s", httpErr.Why)
	}
	return result.Items, nil
}

func findPublicIpById(ids []string) ([]orcapi.PublicIp, error) {
	publicIps, err := retrievePublicIps()
	found := make([]orcapi.PublicIp, 0)
	if err != nil {
		return found, err
	}
	for _, id := range ids {
		for _, ip := range publicIps {
			if id == ip.Id {
				found = append(found, ip)
			}
		}
	}
	if len(found) > 0 {
		return found, nil
	}
	return nil, fmt.Errorf("public ip not found")
}

func printPublicIps(wsName string, publicIps []orcapi.PublicIp) {
	t := termio.Table{}
	makeHeader(&t)
	for _, ip := range publicIps {
		printPublicIpCells(&t, wsName, ip)
	}
	t.Print()
}

func (c PublicIPListCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	wsName, err := setActiveWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}

	result, err := retrievePublicIps()
	if err != nil {
		return err
	}
	foundIps := result
	if len(c.IPAddress) > 0 {
		found, err := findPublicIpByIpRange(c.IPAddress, result)
		if err != nil {
			return err
		}
		foundIps = found
	}
	printPublicIps(wsName, foundIps)
	return nil
}

func (c PublicIPGetCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	wsName, err := setActiveWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	foundIps, err := findPublicIpById(c.Id)
	if err != nil {
		return err
	}
	printPublicIps(wsName, foundIps)
	return nil
}

func findPublicIpByIpRange(ips []string, publicIps []orcapi.PublicIp) ([]orcapi.PublicIp, error) {
	foundPublicIps := make([]orcapi.PublicIp, 0)
	for _, ip := range ips {
		ipRange, err := shared.ParseIPRange(ip)
		if err != nil {
			return nil, err
		}
		for _, publicIp := range publicIps {
			if ipRange.Contains(publicIp.Status.IpAddress.GetOrDefault("")) {
				foundPublicIps = append(foundPublicIps, publicIp)
			}
		}
	}
	if len(foundPublicIps) > 0 {
		return foundPublicIps, nil
	}
	return nil, fmt.Errorf("no public ip found")
}

func findPublicIpByIds(ids []string, publicIps []orcapi.PublicIp) ([]orcapi.PublicIp, error) {
	foundPublicIps := make([]orcapi.PublicIp, 0)
	notFound := make([]string, 0)
	for _, id := range ids {
		found := false
		for _, publicIp := range publicIps {
			if id == publicIp.Id {
				foundPublicIps = append(foundPublicIps, publicIp)
				found = true
				break
			}
		}
		if !found {
			notFound = append(notFound, id)
		}
	}
	if len(notFound) > 0 {
		fmt.Printf("public ip with id %s wasn't found\n", notFound)
	}
	if len(foundPublicIps) > 0 {
		return foundPublicIps, nil
	}
	return nil, fmt.Errorf("public ip with id %s wasn't found", notFound)
}

func (c PublicIPDeleteCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := setActiveWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	result, err := retrievePublicIps()
	if err != nil {
		return err
	}

	toBeDeleted := make([]orcapi.PublicIp, 0)
	if len(c.IPAddress) > 0 {
		found, err := findPublicIpByIpRange(c.IPAddress, result)
		if err != nil {
			return err
		}
		toBeDeleted = append(toBeDeleted, found...)
	}
	if len(c.Ids) > 0 {
		found, err := findPublicIpByIds(c.Ids, result)
		if err != nil {
			return err
		}
		toBeDeleted = append(toBeDeleted, found...)
	}

	if len(toBeDeleted) == 0 {
		return fmt.Errorf("no public ip found")
	}

	deleteBulk := fnd.BulkRequest[fnd.FindByStringId]{}
	deleteMessage := ""
	for _, ip := range toBeDeleted {
		deleteMessage += fmt.Sprintf("%v\n", ip.Id)
		deleteBulk.Items = append(deleteBulk.Items, fnd.FindByStringId{Id: ip.Id})
	}
	_, httpErr := orcapi.PublicIpsDelete.Invoke(deleteBulk)
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to delete public ip: %s", httpErr.Why)
	}
	fmt.Printf("Successfully deleted:\n")
	fmt.Printf("%v\n", deleteMessage)
	return nil

}

func extractProtocol(rule string) (string, orcapi.IpProtocol, error) {
	splt := strings.Split(rule, "/")
	proto := ""
	if len(splt) == 2 && splt[1] != "" {
		proto = strings.ToUpper(splt[1])
	} else {
		return "", "", fmt.Errorf("protocol was not specified: %s", rule)
	}
	return splt[0], orcapi.IpProtocol(proto), nil
}

func extractPortRange(rule string) (int, int, error) {
	splt := strings.Split(rule, "-")
	if len(splt) == 2 && splt[1] != "" {
		start, err := strconv.Atoi(splt[0])
		if err != nil {
			return 0, 0, fmt.Errorf("start port is not a number")
		}
		end, err := strconv.Atoi(splt[1])
		if err != nil {
			return 0, 0, fmt.Errorf("end port is not a number")
		}
		return start, end, nil
	}
	if len(splt) == 1 {
		start, err := strconv.Atoi(splt[0])
		if err != nil {
			return 0, 0, fmt.Errorf("start port is not a number")
		}
		return start, start, nil
	}
	return 0, 0, fmt.Errorf("invalid port range: %s", rule)
}

func createPortRangeAndProto(rules []string) ([]orcapi.PortRangeAndProto, error) {
	portRanges := make([]orcapi.PortRangeAndProto, 0)

	for _, rule := range rules {
		ports, protocol, err := extractProtocol(rule)
		if err != nil {
			return nil, err
		}
		start, end, err := extractPortRange(ports)
		if err != nil {
			return nil, err
		}
		portRanges = append(portRanges, orcapi.PortRangeAndProto{
			Start:    start,
			End:      end,
			Protocol: protocol,
		})
	}
	return portRanges, nil
}

func setActiveWorkspace(cfg *shared.Config, wsName string) (string, error) {
	found := ""
	if wsName != "" {
		ws, err := FindWorkspaceByName(wsName)
		if err != nil {
			return "", err
		}
		if ws != nil {
			found = ws.Name
			shared.SetActiveWorkspace(ws.Id)
		}
	} else {
		getWs, err := shared.GetActiveWorkspace(cfg)
		if err != nil {
			return found, err
		}
		ws, err := FindWorkspaceByName(getWs)
		if err != nil {
			return found, err
		}
		shared.SetActiveWorkspace(ws.Id)
		found = ws.Name
	}
	return found, nil
}

func (c PublicIPCreateCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := setActiveWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	ports, err := createPortRangeAndProto(c.Rule)
	if err != nil {
		return err
	}

	res, httpErr := orcapi.PublicIpsCreate.Invoke(fnd.BulkRequest[orcapi.PublicIPSpecification]{
		Items: []orcapi.PublicIPSpecification{{
			ResourceSpecification: orcapi.ResourceSpecification{
				Product: acc.ProductReference{
					Id:       "public-ip",
					Category: "public-ip",
					Provider: c.Provider,
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
	fmt.Printf("Successfully create public ip %v\n", res.Responses[0].Id)
	return nil
}
