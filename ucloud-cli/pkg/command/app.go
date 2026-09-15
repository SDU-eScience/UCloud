package command

import (
	"fmt"

	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type AppListCommand struct {
	Category string `flag:"category" usage:"Application category"`
}

type AppCategoriesCommand struct{}
type AppSearchCommand struct {
	Application string `positional:"application" usage:"Application name"`
}

type AppGetCommand struct {
	Application string `positional:"application" usage:"Application name"`
}

var AppCommands = map[string]CommandFunc{
	"categories": func() Command { return &AppCategoriesCommand{} },
	"list":       func() Command { return &AppListCommand{} },
	"search":     func() Command { return &AppSearchCommand{} },
	"get":        func() Command { return &AppGetCommand{} },
}

// esult, httpErr := fndapi.ProjectBrowse.Invoke(fndapi.ProjectBrowseRequest{})
// if httpErr.AsError() != nil {
// return map[string]fndapi.Project{}, fmt.Errorf("failed to list workspaces: %s", httpErr.Why)
// }
// workspaces := make(map[string]fndapi.Project)
// for _, workspace := range result.Items {
// repoName := shared.RepositoryProjectName(workspace.Specification.Title)
// workspaces[repoName] = workspace
//
// }
// return workspaces, nil
// return fndapi.PageV2[T]{
// Items:        items,
// Next:         newNext,
// ItemsPerPage: itemsPerPage,
// }
func retrieveAppCategories() (map[string]orcapi.ApplicationCategory, error) {
	// We need to list all categories

	categories := make(map[string]orcapi.ApplicationCategory)

	result, httpErr := orcapi.AppsBrowseStudioCategories.Invoke(orcapi.AppCatalogBrowseStudioCategoriesRequest{})
	if httpErr.AsError() != nil {
		return categories, fmt.Errorf("failed to list categories: %s", httpErr.Why)
	}
	for _, category := range result.Items {
		repoName := shared.RepositoryProjectName(category.Specification.Title)
		categories[repoName] = category
	}
	return categories, nil

	//result, httpErr := orcapi.AppsRetrieveCategory.Invoke(orcapi.AppCatalogRetrieveCategoryRequest{
	//	Id:        0,
	//	Discovery: util.Option[orcapi.CatalogDiscoveryMode]{},
	//	Selected:  util.Option[string]{},
	//})
	//if httpErr.AsError() != nil {
	//	//return map[string]ApplicationCategory{}, fmt.Errorf("failed to list workspaces: %s", httpErr.Why)
	//}
	//appCategories := make(map[string]ApplicationCategory)
	//for name, value := range result.Items {
	//	appCategories[] = value
	//}

}

func (c AppCategoriesCommand) Execute() error {
	shared.InitializeUCloudClient()
	categories, err := retrieveAppCategories()
	if err != nil {
		return fmt.Errorf("failed to retrieve app categories: %s", err)
	}
	t := termio.Table{}
	t.AppendHeader("Category")
	t.AppendHeader("Description")
	for _, v := range categories {
		t.Cell("%v", shared.RepositoryProjectName(v.Specification.Title))
		t.Cell("%v", v.Specification.Description.GetOrDefault(""))
	}
	t.Print()
	return nil
}
func retrieveApps(category string) (map[string]orcapi.ApplicationCategory, error) {
	categories, err := retrieveAppCategories()
	if err != nil {
		return map[string]orcapi.ApplicationCategory{}, fmt.Errorf("failed to retrieve app categories: %s", err)
	}
	if category != "" {
		found, ok := categories[category]
		if !ok {
			return map[string]orcapi.ApplicationCategory{}, fmt.Errorf("category %s not found", category)
		}
		res, httpErr := orcapi.AppsRetrieveCategory.Invoke(
			orcapi.AppCatalogRetrieveCategoryRequest{
				Id: found.Metadata.Id,
			},
		)
		if httpErr.AsError() != nil {
			return map[string]orcapi.ApplicationCategory{},
				fmt.Errorf("failed to retrieve app category: %s", httpErr.Why)
		}
		found.Status.Groups = res.Status.Groups
		categories[category] = found
		return categories, nil
	}
	for key, v := range categories {
		res, httpErr := orcapi.AppsRetrieveCategory.Invoke(
			orcapi.AppCatalogRetrieveCategoryRequest{
				Id: v.Metadata.Id,
			},
		)

		if httpErr.AsError() != nil {
			return map[string]orcapi.ApplicationCategory{},
				fmt.Errorf("failed to retrieve app category: %s", httpErr.Why)
		}

		v.Status.Groups = res.Status.Groups
		categories[key] = v
	}

	return categories, nil
}

func (c AppListCommand) Execute() error {
	shared.InitializeUCloudClient()
	categories, err := retrieveApps(c.Category)
	if err != nil {
		return err
	}
	if c.Category != "" {
		fmt.Printf("Applications in category %s\n", c.Category)
	}

	for _, v := range categories {
		for _, group := range v.Status.Groups {
			t := termio.Table{}
			t.AppendHeader("Flavor")
			t.AppendHeader("Title")
			t.AppendHeader("Description")
			//t.Cell("%v", group.Specification.Title)
			t.Cell("%v", group.Specification.DefaultFlavor)
			t.Cell("%v", group.Specification.Title)
			t.Cell("%v", group.Specification.Description)
			t.Print()
		}
	}
	return nil
}

func (c AppSearchCommand) Execute() error {
	return fmt.Errorf("app search not implemented")
}

func (c AppGetCommand) Execute() error {
	return fmt.Errorf("app get not implemented")
}
