package orchestrator

import (
	"fmt"
	"github.com/blevesearch/bleve"
	orcapi "ucloud.dk/shared/pkg/orc2"
)

var appIndex bleve.Index

func initAppSearchIndex() {
	idx := bleve.NewIndexMapping()

	index, err := bleve.NewMemOnly(idx)
	if err != nil {
		panic(err)
	}

	appIndex = index
}

func appAddToIndex(id AppGroupId, group orcapi.ApplicationGroup) {
	var toIndex struct {
		Title       string
		Description string
		Flavor      []string
	}

	toIndex.Title = group.Specification.Title
	toIndex.Description = group.Specification.Description
	for _, app := range group.Status.Applications {
		if app.Metadata.FlavorName != "" {
			toIndex.Flavor = append(toIndex.Flavor, app.Metadata.FlavorName)
		}
	}
	err := appIndex.Index(fmt.Sprint(id), toIndex)
	if err != nil {
		panic(err)
	}
}
