package exec

import (
	"ucloud.dk/gonja/v2/nodes"
)

type ControlStructure interface {
	nodes.ControlStructure
	Execute(*Renderer, *nodes.ControlStructureBlock) error
}
