package accounting

import (
	"github.com/blevesearch/bleve/v2"
	"ucloud.dk/shared/pkg/log"
)

var grantIndex bleve.Index

func initGrantSearchIndex() {
	idx := bleve.NewIndexMapping()

	index, err := bleve.NewMemOnly(idx)
	if err != nil {
		panic(err)
	}
	grantIndex = index
}

func grantAddToSearchIndex(grant *grantApplication) {
	var toIndex struct {
		CreatedBy []string
	}
	toIndex.CreatedBy = append(toIndex.CreatedBy, grant.Application.CreatedBy)
	log.Debug("----- " + grant.Application.CreatedBy + " -----")

	err := grantIndex.Index(grant.Application.Id.Value, toIndex)
	if err != nil {
		panic(err)
	}
}
