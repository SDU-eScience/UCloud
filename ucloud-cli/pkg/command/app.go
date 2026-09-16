package command

import (
	"fmt"
	"strings"

	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/ucloud_cli/pkg/shared"
)

type AppListCommand struct {
	Category string `flag:"category" usage:"Application category"`
}

type AppCategoriesCommand struct{}
type AppSearchCommand struct {
	Query string `positional:"query" usage:"Search query"`
}

type AppGetCommand struct {
	Application string `positional:"application" usage:"Application name" required:"true"`
}

var AppCommands = map[string]CommandFunc{
	"categories": func() Command { return &AppCategoriesCommand{} },
	"list":       func() Command { return &AppListCommand{} },
	"search":     func() Command { return &AppSearchCommand{} },
	"get":        func() Command { return &AppGetCommand{} },
}

func retrieveAppCategories() (map[string]orcapi.ApplicationCategory, error) {
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
func retrieveApps(category string, appName string) (map[string]orcapi.ApplicationCategory, *orcapi.Application, error) {
	categories, err := retrieveAppCategories()
	if err != nil {
		return map[string]orcapi.ApplicationCategory{}, nil, fmt.Errorf("failed to retrieve app categories: %s", err)
	}
	if category != "" {
		found, ok := categories[category]
		if !ok {
			return map[string]orcapi.ApplicationCategory{}, nil, fmt.Errorf("category %s not found", category)
		}
		res, httpErr := orcapi.AppsRetrieveCategory.Invoke(
			orcapi.AppCatalogRetrieveCategoryRequest{
				Id: found.Metadata.Id,
			},
		)
		if httpErr.AsError() != nil {
			return map[string]orcapi.ApplicationCategory{}, nil,
				fmt.Errorf("failed to retrieve app category: %s", httpErr.Why)
		}
		found.Status.Groups = res.Status.Groups
		categories[category] = found
		if appName != "" {
			foundApp := findAppInGroup(found, strings.Split(appName, ":"))
			return categories, foundApp, nil
		}
		return categories, nil, nil
	}

	// If no category is specified, we need to retrieve all categories

	var foundApp *orcapi.Application
	for key, v := range categories {
		// Calling AppsRetrieveCategory for each category, maybe create a new endpoint for this
		res, httpErr := orcapi.AppsRetrieveCategory.Invoke(
			orcapi.AppCatalogRetrieveCategoryRequest{
				Id: v.Metadata.Id,
			},
		)

		if httpErr.AsError() != nil {
			return map[string]orcapi.ApplicationCategory{}, nil,
				fmt.Errorf("failed to retrieve app category: %s", httpErr.Why)
		}

		v.Status.Groups = res.Status.Groups
		categories[key] = v

		if appName != "" {
			// since we are already looping, then we can also find the app that the user is looking for
			foundApp = findAppInGroup(v, strings.Split(appName, ":"))
		}
	}

	return categories, foundApp, nil
}

func findAppInGroup(v orcapi.ApplicationCategory, nameVersion []string) *orcapi.Application {
	// since we are already looping, then we can also find the app that the user is looking for
	for _, group := range v.Status.Groups {
		foundGroup, httpErr := orcapi.AppsRetrieveGroup.Invoke(orcapi.AppCatalogRetrieveGroupRequest{Id: int64(group.Metadata.Id)})
		if httpErr.AsError() != nil {
			return nil
		}
		group.Status.Applications = foundGroup.Status.Applications
		for _, app := range group.Status.Applications {
			if len(nameVersion) > 1 {
				if shared.RepositoryProjectName(app.Metadata.Name) == nameVersion[0] {
					if nameVersion[1] == "" || app.Metadata.Version == nameVersion[1] {
						return &app
					}
				}
			} else {
				if shared.RepositoryProjectName(app.Metadata.Name) == nameVersion[0] {
					return &app
				}
			}
		}
	}
	return nil
}

func (c AppListCommand) Execute() error {
	shared.InitializeUCloudClient()
	categories, _, err := retrieveApps(c.Category, "")
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
			t.Cell("%v", group.Specification.DefaultFlavor)
			t.Cell("%v", group.Specification.Title)
			t.Cell("%v", group.Specification.Description)
			t.Print()
		}
	}
	return nil
}

func searchForApp(query string) ([]orcapi.Application, error) {
	shared.InitializeUCloudClient()
	result, httpErr := orcapi.AppsSearch.Invoke(orcapi.AppCatalogSearchRequest{
		Query: query,
	})
	if httpErr.AsError() != nil {
		return nil, fmt.Errorf("failed to search for applications: %s", httpErr.Why)
	}
	return result.Items, nil
}

func (c AppSearchCommand) Execute() error {
	apps, err := searchForApp(c.Query)
	if err != nil {
		return err
	}
	t := termio.Table{}
	t.AppendHeader("Title")
	t.AppendHeader("Description")
	t.AppendHeader("Flavor")
	for _, app := range apps {
		t.Cell("%v", app.Metadata.Title)
		t.Cell("%v", app.Metadata.Description)
		t.Cell("%v", app.Metadata.FlavorName.GetOrDefault(""))
	}
	t.Print()
	return nil
}

func (c AppGetCommand) Execute() error {
	shared.InitializeUCloudClient()
	_, found, err := retrieveApps("", c.Application)
	if err != nil {
		return err
	}
	if found == nil {
		return fmt.Errorf("application %s not found", c.Application)
	}
	t := termio.Table{}
	t.AppendHeader("Title")
	t.AppendHeader("Description")
	t.AppendHeader("Flavor")
	t.Cell("%v", found.Metadata.Title)
	t.Cell("%v", found.Metadata.Description)
	t.Cell("%v", found.Metadata.FlavorName.GetOrDefault(""))
	t.Print()

	return nil

}
