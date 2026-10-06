package ucxapi

import (
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type EditorSchemaRegistration struct {
	SchemaId string
	Revision string
	Schema   map[string]any
}

type EditorRegisterSchemasRequest struct {
	Schemas []EditorSchemaRegistration
}

type EditorUnregisterSchemasRequest struct {
	SchemaIds []string
}

var EditorRegisterSchemas = ucx.Rpc[EditorRegisterSchemasRequest, util.Empty]{CallName: "editorRegisterSchemas"}
var EditorUnregisterSchemas = ucx.Rpc[EditorUnregisterSchemasRequest, util.Empty]{CallName: "editorUnregisterSchemas"}
