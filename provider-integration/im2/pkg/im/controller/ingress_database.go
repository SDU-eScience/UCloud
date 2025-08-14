package controller

import (
	"encoding/json"
	"strings"
	"sync"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var ingresses = map[string]*orc.Ingress{}

var ingressesMutex = sync.Mutex{}

func initIngressDatabase() {
	if !RunsServerCode() {
		return
	}

	ingressesMutex.Lock()
	defer ingressesMutex.Unlock()
	fetchAllLinks()
}

func fetchAllLinks() {
	result := db.NewTx(func(tx *db.Transaction) []*orc.Ingress {
		var result []*orc.Ingress
		rows := db.Select[struct{ Resource string }](
			tx,
			`
				select resource from tracked_ingresses
		    `,
			db.Params{},
		)

		for _, row := range rows {
			var ing orc.Ingress
			err := json.Unmarshal([]byte(row.Resource), &ing)
			if err == nil {
				result = append(result, &ing)
			}
		}
		return result
	})

	for _, row := range result {
		ingresses[row.Id] = row
	}
}

func TrackLink(ingress orc.Ingress) {
	// Automatically assign timestamps to all updates that do not have one
	for i := 0; i < len(ingress.Updates); i++ {
		update := &ingress.Updates[i]
		if update.Timestamp.UnixMilli() <= 0 {
			update.Timestamp = fnd.Timestamp(time.Now())
		}
	}

	ingressesMutex.Lock()
	ingresses[ingress.Id] = &ingress
	ingressesMutex.Unlock()

	jsonified, _ := json.Marshal(ingress)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into tracked_ingresses(resource_id, created_by, project_id, product_id, product_category, resource)
				values (:resource_id, :created_by, :project_id, :product_id, :product_category, :resource)
				on conflict (resource_id) do update set
					resource = excluded.resource,
					created_by = excluded.created_by,
					project_id = excluded.project_id,
					product_id = excluded.product_id,
					product_category = excluded.product_category
			`,
			db.Params{
				"resource_id":      ingress.Id,
				"created_by":       ingress.Owner.CreatedBy,
				"project_id":       ingress.Owner.Project,
				"product_id":       ingress.Specification.Product.Id,
				"product_category": ingress.Specification.Product.Category,
				"resource":         string(jsonified),
			},
		)
	})
}

func DeleteTrackedLink(target *orc.Ingress, dbFn func(tx *db.Transaction)) error {
	if len(target.Status.BoundTo) > 0 {
		return util.UserHttpError("This link is currently in use by job: %v", strings.Join(target.Status.BoundTo, ", "))
	}

	ingressesMutex.Lock()

	delete(ingresses, target.Id)

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from tracked_ingresses
				where resource_id = :id
			`,
			db.Params{
				"id": target.Id,
			},
		)

		dbFn(tx)
	})

	ingressesMutex.Unlock()
	return nil
}

func RetrieveIngress(id string) orc.Ingress {
	ingressesMutex.Lock()
	result := *ingresses[id]
	ingressesMutex.Unlock()
	return result
}

func RetrieveUsedLinkCount(owner orc.ResourceOwner) int {
	return db.NewTx[int](func(tx *db.Transaction) int {
		row, _ := db.Get[struct{ Count int }](
			tx,
			`
				select count(*) as count
				from tracked_ingresses
				where
					(:project = '' and project_id is null and created_by = :created_by)
					or (:project != '' and project_id = :project)
		    `,
			db.Params{
				"created_by": owner.CreatedBy,
				"project":    owner.Project,
			},
		)
		return row.Count
	})
}
