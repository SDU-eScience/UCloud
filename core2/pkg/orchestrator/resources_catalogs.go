package orchestrator

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type ResourceCatalog[Resc any, Spec any] struct {
	Type       string
	Retrieve   func(actor rpc.Actor, id string, flags orcapi.ResourceFlags) (Resc, *util.HttpError)
	Browse     func(actor rpc.Actor, itemsPerPage int, next util.Option[string], flags orcapi.ResourceFlags) (fndapi.PageV2[Resc], *util.HttpError)
	Delete     func(actor rpc.Actor, id string) *util.HttpError
	SpecToBase func(spec Spec) orcapi.ResourceSpecification
	RescToBase func(spec Resc) orcapi.ResourceSpecification
}

var ResourceCatalogs struct {
	Jobs        ResourceCatalog[orcapi.Job, orcapi.JobSpecification]
	Licenses    ResourceCatalog[orcapi.License, orcapi.LicenseSpecification]
	PublicIps   ResourceCatalog[orcapi.PublicIp, orcapi.PublicIPSpecification]
	PublicLinks ResourceCatalog[orcapi.Ingress, orcapi.IngressSpecification]
	Networks    ResourceCatalog[orcapi.PrivateNetwork, orcapi.PrivateNetworkSpecification]
}

func initResourceCatalogs() {
	ResourceCatalogs.Jobs = ResourceCatalog[orcapi.Job, orcapi.JobSpecification]{
		Type: jobType,
		Retrieve: func(actor rpc.Actor, id string, flags orcapi.ResourceFlags) (orcapi.Job, *util.HttpError) {
			return JobsRetrieve(actor, id, orcapi.JobFlags{ResourceFlags: flags})
		},
		Browse: func(actor rpc.Actor, itemsPerPage int, next util.Option[string], flags orcapi.ResourceFlags) (fndapi.PageV2[orcapi.Job], *util.HttpError) {
			return JobsBrowse(actor, next, itemsPerPage, orcapi.JobFlags{ResourceFlags: flags})
		},
		Delete: func(actor rpc.Actor, id string) *util.HttpError {
			_, err := JobsTerminateBulk(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		SpecToBase: func(spec orcapi.JobSpecification) orcapi.ResourceSpecification {
			return spec.ResourceSpecification
		},
		RescToBase: func(spec orcapi.Job) orcapi.ResourceSpecification {
			return spec.Specification.ResourceSpecification
		},
	}

	ResourceCatalogs.Licenses = ResourceCatalog[orcapi.License, orcapi.LicenseSpecification]{
		Type: licenseType,
		Retrieve: func(actor rpc.Actor, id string, flags orcapi.ResourceFlags) (orcapi.License, *util.HttpError) {
			return ResourceRetrieve[orcapi.License](actor, licenseType, ResourceParseId(id), flags)
		},
		Browse: func(actor rpc.Actor, itemsPerPage int, next util.Option[string], flags orcapi.ResourceFlags) (fndapi.PageV2[orcapi.License], *util.HttpError) {
			return LicenseBrowse(actor, orcapi.LicensesBrowseRequest{Next: next, ItemsPerPage: itemsPerPage, LicenseFlags: orcapi.LicenseFlags{ResourceFlags: flags}}), nil
		},
		Delete: func(actor rpc.Actor, id string) *util.HttpError {
			_, err := LicenseDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		SpecToBase: func(spec orcapi.LicenseSpecification) orcapi.ResourceSpecification {
			return spec.ResourceSpecification
		},
		RescToBase: func(spec orcapi.License) orcapi.ResourceSpecification {
			return spec.Specification.ResourceSpecification
		},
	}

	ResourceCatalogs.PublicIps = ResourceCatalog[orcapi.PublicIp, orcapi.PublicIPSpecification]{
		Type: publicIpType,
		Retrieve: func(actor rpc.Actor, id string, flags orcapi.ResourceFlags) (orcapi.PublicIp, *util.HttpError) {
			return ResourceRetrieve[orcapi.PublicIp](actor, publicIpType, ResourceParseId(id), flags)
		},
		Browse: func(actor rpc.Actor, itemsPerPage int, next util.Option[string], flags orcapi.ResourceFlags) (fndapi.PageV2[orcapi.PublicIp], *util.HttpError) {
			return PublicIpBrowse(actor, orcapi.PublicIpsBrowseRequest{Next: next, ItemsPerPage: itemsPerPage, PublicIpFlags: orcapi.PublicIpFlags{ResourceFlags: flags}}), nil
		},
		Delete: func(actor rpc.Actor, id string) *util.HttpError {
			_, err := PublicIpDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		SpecToBase: func(spec orcapi.PublicIPSpecification) orcapi.ResourceSpecification {
			return spec.ResourceSpecification
		},
		RescToBase: func(spec orcapi.PublicIp) orcapi.ResourceSpecification {
			return spec.Specification.ResourceSpecification
		},
	}

	ResourceCatalogs.PublicLinks = ResourceCatalog[orcapi.Ingress, orcapi.IngressSpecification]{
		Type: ingressType,
		Retrieve: func(actor rpc.Actor, id string, flags orcapi.ResourceFlags) (orcapi.Ingress, *util.HttpError) {
			return ResourceRetrieve[orcapi.Ingress](actor, ingressType, ResourceParseId(id), flags)
		},
		Browse: func(actor rpc.Actor, itemsPerPage int, next util.Option[string], flags orcapi.ResourceFlags) (fndapi.PageV2[orcapi.Ingress], *util.HttpError) {
			return IngressBrowse(actor, orcapi.IngressesBrowseRequest{Next: next, ItemsPerPage: itemsPerPage, IngressFlags: orcapi.IngressFlags{ResourceFlags: flags}}), nil
		},
		Delete: func(actor rpc.Actor, id string) *util.HttpError {
			_, err := IngressDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
			return err
		},
		SpecToBase: func(spec orcapi.IngressSpecification) orcapi.ResourceSpecification {
			return spec.ResourceSpecification
		},
		RescToBase: func(spec orcapi.Ingress) orcapi.ResourceSpecification {
			return spec.Specification.ResourceSpecification
		},
	}

	ResourceCatalogs.Networks = ResourceCatalog[orcapi.PrivateNetwork, orcapi.PrivateNetworkSpecification]{
		Type: privateNetworkType,
		Retrieve: func(actor rpc.Actor, id string, flags orcapi.ResourceFlags) (orcapi.PrivateNetwork, *util.HttpError) {
			return PrivateNetworkRetrieve(actor, orcapi.PrivateNetworksRetrieveRequest{Id: id, PrivateNetworkFlags: orcapi.PrivateNetworkFlags{ResourceFlags: flags}})
		},
		Browse: func(actor rpc.Actor, itemsPerPage int, next util.Option[string], flags orcapi.ResourceFlags) (fndapi.PageV2[orcapi.PrivateNetwork], *util.HttpError) {
			return PrivateNetworkBrowse(actor, orcapi.PrivateNetworksBrowseRequest{Next: next, ItemsPerPage: itemsPerPage, PrivateNetworkFlags: orcapi.PrivateNetworkFlags{ResourceFlags: flags}}), nil
		},
		Delete: func(actor rpc.Actor, id string) *util.HttpError {
			return PrivateNetworkDelete(actor, fndapi.BulkRequestOf(fndapi.FindByStringId{Id: id}))
		},
		SpecToBase: func(spec orcapi.PrivateNetworkSpecification) orcapi.ResourceSpecification {
			return spec.ResourceSpecification
		},
		RescToBase: func(spec orcapi.PrivateNetwork) orcapi.ResourceSpecification {
			return spec.Specification.ResourceSpecification
		},
	}
}
