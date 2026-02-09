package accounting

import (
	"github.com/blevesearch/bleve/v2"
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
		CreatedBy      []string
		RecipientTitle []string
		RecipientId    []string
		ReferenceIds   []string
		Comments       []string
	}
	toIndex.CreatedBy = append(toIndex.CreatedBy, grant.Application.CreatedBy)
	toIndex.RecipientTitle = append(toIndex.RecipientTitle, grantGetRecipientTitleByType(&grant.Application.CurrentRevision.Document.Recipient))
	toIndex.RecipientId = append(toIndex.RecipientId, grant.Application.CurrentRevision.Document.Recipient.Id.Value)
	toIndex.ReferenceIds = append(toIndex.ReferenceIds, grant.Application.CurrentRevision.Document.ReferenceIds.Get()...)

	for _, c := range grant.Application.Status.Comments {
		toIndex.Comments = append(toIndex.Comments, c.Comment)
	}

	err := grantIndex.Index(grant.Application.Id.Value, toIndex)
	if err != nil {
		panic(err)
	}
}
