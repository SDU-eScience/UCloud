package k8s

import (
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func createPrivateNetworkIp(ip *orc.PrivateNetworkIp) *util.HttpError {
	return controller.PrivateNetworkReservationCreate(ip)
}

func deletePrivateNetworkIp(ip *orc.PrivateNetworkIp) *util.HttpError {
	return controller.PrivateNetworkReservationDelete(ip)
}

func retrievePrivateNetworkIpProducts() []orc.PrivateNetworkIpSupport {
	return shared.PrivateNetworkIpSupport
}
