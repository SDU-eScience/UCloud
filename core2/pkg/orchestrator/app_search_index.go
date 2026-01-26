package orchestrator

import (
	"fmt"
	"strings"

	"github.com/blevesearch/bleve/v2"
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

func appAddToSearchIndex(id AppGroupId, group orcapi.ApplicationGroup) {
	var toIndex struct {
		Title       string
		Description string
		Flavor      string
	}

	toIndex.Title = group.Specification.Title
	toIndex.Description = group.Specification.Description
	flavors := strings.Builder{}
	for _, app := range group.Status.Applications {
		flavors.WriteString(app.Metadata.FlavorName.Value)
		flavors.WriteString(" ")
	}
	toIndex.Flavor = flavors.String()
	err := appIndex.Index(fmt.Sprint(id), toIndex)
	if err != nil {
		panic(err)
	}
}
