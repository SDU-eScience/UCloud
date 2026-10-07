package command

import (
	"fmt"
	"regexp"

	apm "ucloud.dk/shared/pkg/accounting"
	"ucloud.dk/shared/pkg/cli"
	fnd "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

var linkNameRegex = regexp.MustCompile(`-(.*?)\.`)

type PublicLinkListCommand struct {
	Workspace string `flag:"workspace" usage:"Workspace to list links from"`
}

type PublicLinkGetCommand struct {
	Name      string `positional:"name" usage:"Public link name"`
	Workspace string `flag:"workspace" usage:"Workspace to get link from"`
}

type PublicLinkCreateCommand struct {
	Provider  string `flag:"provider"`
	Workspace string `flag:"workspace"`
	Name      string `positional:"name" usage:"Public link name"`
	Domain    string `flag:"domain" usage:"Domain"`
	Product   string `flag:"product" usage:"Product"`
}

type PublicLinkDeleteCommand struct {
	Name      []string `positional:"name" usage:"Public link name"`
	Workspace string   `flag:"workspace" usage:"Workspace to delete link from"`
}

var PublicLinkCommands = map[string]CommandFunc{
	"list": func() Command { return &PublicLinkListCommand{} },
	"get":  func() Command { return &PublicLinkGetCommand{} },
	"create": func() Command {
		return &PublicLinkCreateCommand{}
	},
	"delete": func() Command {
		return &PublicLinkDeleteCommand{}
	},
}

func retrievePublicLinks() (map[string]orcapi.Ingress, error) {
	result, httpErr := orcapi.IngressesBrowse.Invoke(orcapi.IngressesBrowseRequest{})
	links := make(map[string]orcapi.Ingress)
	if httpErr.AsError() != nil {
		return nil, fmt.Errorf("failed to list public links: %s", httpErr.Why)
	}
	match := ""
	for _, link := range result.Items {
		matches := linkNameRegex.FindStringSubmatch(link.Specification.Domain)
		if len(matches) > 1 {
			match = matches[1]
		}
		links[match] = link
		match = ""
	}
	return links, nil
}

func printPublicLinks(links map[string]orcapi.Ingress) {
	t := termio.Table{}
	t.AppendHeader("Name")
	t.AppendHeader("Link")
	t.AppendHeader("State")
	t.AppendHeader("CreatedAt")
	for name, v := range links {
		t.Cell("%v", name)
		t.Cell("%v", v.Specification.Domain)
		t.Cell("%v", v.Status.State)
		t.Cell("%v", cli.FormatTime(v.CreatedAt))
	}
	t.Print()
}
func (c PublicLinkListCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	res, e := retrievePublicLinks()
	if e != nil {
		return e
	}
	printPublicLinks(res)
	return nil
}

func (c PublicLinkGetCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	retreived, err := retrievePublicLinks()
	if err != nil {
		return err
	}
	link, ok := retreived[c.Name]
	if !ok {
		return fmt.Errorf("public link %s not found", c.Name)
	}
	printPublicLinks(map[string]orcapi.Ingress{c.Name: link})
	return nil
}

func (c PublicLinkCreateCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}

	products, httpErr := orcapi.IngressesRetrieveProducts.Invoke(util.Empty{})
	if httpErr.AsError() != nil {
		return fmt.Errorf("failed to retrieve products: %s", httpErr.Why)
	}
	if len(products.ProductsByProvider) == 0 {
		return fmt.Errorf("no products found")
	}
	supports, ok := products.ProductsByProvider[c.Provider]
	if !ok {
		return fmt.Errorf("provider %s not found", c.Provider)
	}
	if len(supports) == 0 {
		return fmt.Errorf("no products found for provider %s", c.Provider)
	}

	createdLinks := make([]string, 0)
	for _, p := range supports {
		createLink := fmt.Sprintf("%s%s%s", p.Support.Prefix, c.Name, p.Support.Suffix)
		createdLinks = append(createdLinks, createLink)
	}

	request := fnd.BulkRequest[orcapi.IngressSpecification]{
		Items: []orcapi.IngressSpecification{{
			Domain: createdLinks[0],
			ResourceSpecification: orcapi.ResourceSpecification{
				Product: apm.ProductReference{
					Id:       "public-links",
					Provider: c.Provider,
					Category: "public-links",
				},
			},
		}},
	}
	_, httpCreateErr := orcapi.IngressesCreate.Invoke(request)
	if httpCreateErr.AsError() != nil {
		return fmt.Errorf("failed to create public link: %s", httpCreateErr.Why)
	}
	fmt.Printf("Successfully created public link %v\n", createdLinks)
	return nil
}

func (c PublicLinkDeleteCommand) Execute() error {
	cfg := shared.InitializeUCloudClient()
	_, err := shared.SetOrUseDefaultWorkspace(cfg, c.Workspace)
	if err != nil {
		return err
	}
	request := fnd.BulkRequest[fnd.FindByStringId]{}

	links, err := retrievePublicLinks()
	if err != nil {
		return err
	}

	deletedLinks := make([]string, 0)
	notFoundLinks := make([]string, 0)
	for _, name := range c.Name {
		found, ok := links[name]
		if ok {
			request.Items = append(request.Items, fnd.FindByStringId{Id: found.Id})
			deletedLinks = append(deletedLinks, name)
		} else {
			notFoundLinks = append(notFoundLinks, name)
		}

	}
	if len(request.Items) == 0 {
		return fmt.Errorf("the names %v were not found", c.Name)
	}
	_, httpDeleteErr := orcapi.IngressesDelete.Invoke(request)
	if httpDeleteErr.AsError() != nil {
		return fmt.Errorf("failed to delete public link: %s", httpDeleteErr.Why)
	}
	fmt.Printf("Successfully deleted public link %v\n", deletedLinks)
	if len(notFoundLinks) > 0 {
		fmt.Printf("public link with name %v wasn't found\n", notFoundLinks)
	}
	return nil
}
