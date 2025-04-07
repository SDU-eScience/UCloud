package controller

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	db "ucloud.dk/pkg/database"
	fnd "ucloud.dk/pkg/foundation"
	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
	"ucloud.dk/pkg/util"
)

var ingresses = map[string]*orc.Ingress{}

var ingressesMutex = sync.Mutex{}

func initIngressDatabase() {
	if !RunsServerCode() {
		return
	}

	ingressesMutex.Lock()
	defer ingressesMutex.Unlock()
	fetchAllIngresses()
}

func fetchAllIngresses() {
	next := ""

	for {
		page, err := orc.BrowseIngresses(next, orc.BrowseIngressesFlags{
			IncludeProduct: false,
			IncludeUpdates: true,
		})

		if err != nil {
			log.Warn("Failed to fetch ingresses: %v", err)
			break
		}

		for i := 0; i < len(page.Items); i++ {
			ingress := &page.Items[i]
			ingresses[ingress.Id] = ingress
		}

		if !page.Next.IsSet() {
			break
		} else {
			next = page.Next.Get()
		}
	}
}

func TrackIngress(ingress orc.Ingress) {
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

func CreateIngress(target *orc.Ingress) error {
	log.Debug("CreateIngress called")
	if target == nil {
		return fmt.Errorf("target is nil")
	}

	status := util.Option[string]{}
	status.Set("Public link is ready for use")

	newUpdate := orc.IngressUpdate{
		State:     util.OptValue(orc.IngressStateReady),
		Timestamp: fnd.Timestamp(time.Now()),
		Status:    status,
	}

	err := orc.UpdateIngresses(fnd.BulkRequest[orc.ResourceUpdateAndId[orc.IngressUpdate]]{
		Items: []orc.ResourceUpdateAndId[orc.IngressUpdate]{
			{
				Id:     target.Id,
				Update: newUpdate,
			},
		},
	})

	if err == nil {
		target.Updates = append(target.Updates, newUpdate)
		TrackIngress(*target)
		return nil
	} else {
		log.Info("Failed to activate license due to an error between UCloud and the provider: %s", err)
		return err
	}
}

func DeleteIngress(target *orc.Ingress) error {
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
	})

	return nil
}

func RetrieveIngress(id string) orc.Ingress {
	ingressesMutex.Lock()
	result := *ingresses[id]
	ingressesMutex.Unlock()
	return result
}
