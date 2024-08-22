package apm

import (
	c "ucloud.dk/pkg/client"
	"ucloud.dk/pkg/util"
)

const integrationControlContext = "/api/providers/integration/control"
const integrationControlNamespace = "providers.im.control"

type InitiateReverseConnectionResponse struct {
	Token string `json:"token"`
}

func InitiateReverseConnection() (InitiateReverseConnectionResponse, error) {
	return c.ApiUpdate[InitiateReverseConnectionResponse](
		integrationControlNamespace+"browse",
		integrationControlContext,
		"reverseConnection",
		util.EmptyValue,
	)
}
