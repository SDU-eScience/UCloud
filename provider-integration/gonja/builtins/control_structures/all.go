package controlStructures

import (
	"ucloud.dk/gonja/v2/exec"
	"ucloud.dk/gonja/v2/parser"
)

var All = exec.NewControlStructureSet(map[string]parser.ControlStructureParser{
	"autoescape": autoescapeParser,
	"block":      blockParser,
	"extends":    extendsParser,
	"filter":     filterParser,
	"for":        forParser,
	"from":       fromParser,
	"if":         ifParser,
	"import":     importParser,
	"include":    includeParser,
	"macro":      macroParser,
	"raw":        rawParser,
	"set":        setParser,
	"with":       withParser,
})

var Safe = exec.NewControlStructureSet(map[string]parser.ControlStructureParser{
	"autoescape": autoescapeParser,
	"filter":     filterParser,
	"for":        forParser,
	"if":         ifParser,
	"macro":      macroParser,
	"raw":        rawParser,
	"set":        setParser,
	"with":       withParser,
})
