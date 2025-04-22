package controller

import (
	"encoding/json"
	"regexp"
	"strings"
	"sync"
	"time"

	cfg "ucloud.dk/pkg/im/config"
	db "ucloud.dk/shared/pkg/database"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var ingresses = map[string]*orc.Ingress{}

var ingressesMutex = sync.Mutex{}

var (
	regex *regexp.Regexp = regexp.MustCompile("[a-z]([-_a-z0-9]){4,255}")
)

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
	if target == nil {
		return util.ServerHttpError("Failed to create public link: target is nil")
	}

	owner := target.Owner.CreatedBy

	if len(target.Owner.Project) > 0 {
		owner = target.Owner.Project
	}

	domain := target.Specification.Domain
	prefix := cfg.Services.Kubernetes().Compute.PublicLinks.Prefix
	suffix := cfg.Services.Kubernetes().Compute.PublicLinks.Suffix

	isValid := strings.HasPrefix(domain, prefix) && strings.HasSuffix(domain, suffix)

	if !isValid {
		return util.UserHttpError("Specified domain is not valid.")
	}

	id, _ := strings.CutPrefix(domain, prefix)
	id, _ = strings.CutSuffix(id, suffix)

	if len(id) < 5 {
		return util.UserHttpError("Public links must be at least 5 characters long.")
	}

	if strings.HasSuffix(id, "-") || strings.HasSuffix(id, "_") {
		return util.UserHttpError("Public links cannot end with a dash or underscore.")
	}

	if !regex.MatchString(id) {
		return util.UserHttpError("Public link must only contain letters a-z, numbers (0-9), dashes and underscores.")
	}

	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				insert into ingresses(domain, owner)
				values (:domain, :owner) on conflict do nothing
			`,
			db.Params{
				"domain": domain,
				"owner":  owner,
			},
		)
	})

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
		log.Warn("Failed to create public link due to an error between UCloud and the provider: %s", err)
		return err
	}
}

func DeleteIngress(target *orc.Ingress) error {
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

		db.Exec(
			tx,
			`
				delete from ingresses
				where domain = :domain
			`,
			db.Params{
				"domain": target.Specification.Domain,
			},
		)
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
