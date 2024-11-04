package orchestrators

import "ucloud.dk/pkg/util"

type WorkflowSpecification struct {
	ApplicationName string                 `json:"applicationName"`
	Language        string                 `json:"language"`
	Init            util.Option[string]    `json:"init"`
	Job             util.Option[string]    `json:"job"`
	Readme          util.Option[string]    `json:"readme"`
	Inputs          []ApplicationParameter `json:"inputs"`
}
