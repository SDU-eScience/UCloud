package controller

import (
	"sync"

	"ucloud.dk/pkg/log"
	orc "ucloud.dk/pkg/orchestrators"
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

func CreateIngress(ingress *orc.Ingress) error {
	return nil
}

func DeleteIngress(ingress *orc.Ingress) error {
	return nil
}
