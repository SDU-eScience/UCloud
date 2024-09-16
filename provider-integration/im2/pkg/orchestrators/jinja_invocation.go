package orchestrators

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"regexp"
	"strings"
	"ucloud.dk/gonja/v2/builtins"
	controlStructures "ucloud.dk/gonja/v2/builtins/control_structures"
	gonjacfg "ucloud.dk/gonja/v2/config"
	"ucloud.dk/gonja/v2/exec"
	"ucloud.dk/gonja/v2/loaders"
	cfg "ucloud.dk/pkg/im/config"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type JinjaFlags int

const (
	JinjaFlagsNoEscape JinjaFlags = 1 << iota
)

var (
	templateRegex = regexp.MustCompile(`{-\s*(\w+)\s*-}`)
)

func createJinjaTemplate(source string, templates map[string]string, flags JinjaFlags) (*exec.Template, error) {
	if cfg.Mode != cfg.ServerModeUser {
		return nil, fmt.Errorf("this function is only implemented for user mode")
	}

	source = templateRegex.ReplaceAllStringFunc(source, func(match string) string {
		templateId := templateRegex.FindStringSubmatch(match)
		replace, ok := templates[templateId[1]]
		if !ok {
			return ""
		} else {
			return replace
		}
	})

	filters := exec.NewFilterSet(map[string]exec.FilterFunction{
		"flag": func(e *exec.Evaluator, in *exec.Value, params *exec.VarArgs) *exec.Value {
			if in.IsError() {
				return in
			}

			if len(params.Args) < 1 || len(params.Args) > 2 {
				return exec.AsValue(fmt.Errorf("wrong signature for 'flag'"))
			}

			if !params.Args[0].IsString() {
				return exec.AsValue(fmt.Errorf("wrong signature for 'flag' expected arg 0 to be a string"))
			}

			trueFlag := params.Args[0].String()
			falseFlag := ""
			if len(params.Args) == 2 {
				if !params.Args[1].IsString() {
					return exec.AsValue(fmt.Errorf("wrong signature for 'flag' expected arg 1 to be a string"))
				}

				falseFlag = params.Args[1].String()
			}

			if in.Bool() {
				if trueFlag == "" {
					return exec.AsSafeValue("")
				}
				return exec.AsValue(trueFlag)
			} else {
				if falseFlag == "" {
					return exec.AsSafeValue("")
				}
				return exec.AsValue(falseFlag)
			}
		},

		"option": func(e *exec.Evaluator, in *exec.Value, params *exec.VarArgs) *exec.Value {
			if in.IsError() {
				return in
			}

			if len(params.Args) < 1 || len(params.Args) >= 3 {
				return exec.AsValue(fmt.Errorf("wrong signature for 'option'"))
			}

			// NOTE(Dan): This will transform non-string values into their string equivalent
			optionFlag := params.Args[0].String()

			addSpaces := !strings.HasSuffix(optionFlag, "=")
			if len(params.Args) == 2 {
				addSpacesArg := params.Args[1]
				if !addSpacesArg.IsBool() {
					return exec.AsValue(fmt.Errorf("wrong signature for 'option' expected arg 2 to be a boolean"))
				}
				addSpaces = addSpacesArg.Bool()
			}

			if in.IsNil() {
				return exec.AsSafeValue("")
			}

			stringifiedInput := in.String()

			if addSpaces {
				return exec.AsSafeValue(fmt.Sprintf("%s %s", optionFlag, EscapeBash(stringifiedInput)))
			} else {
				return exec.AsSafeValue(EscapeBash(optionFlag + stringifiedInput))
			}
		},
	}).Update(builtins.Filters)

	env := &exec.Environment{
		Context:           exec.EmptyContext().Update(builtins.GlobalFunctions),
		Filters:           filters,
		Tests:             builtins.Tests,
		ControlStructures: controlStructures.Safe,
		Methods:           builtins.Methods, // TODO Consider these
	}

	gonjaCfg := gonjacfg.New()
	gonjaCfg.AutoEscape = (flags & JinjaFlagsNoEscape) == 0

	byteSource := []byte(source)

	rootID := fmt.Sprintf("root-%s", string(sha256.New().Sum(byteSource)))

	loader, err := loaders.NewFileSystemLoader("")
	if err != nil {
		return nil, err
	}
	shiftedLoader, err := loaders.NewShiftedLoader(rootID, bytes.NewReader(byteSource), loader)
	if err != nil {
		return nil, err
	}

	template, err := exec.NewTemplate(rootID, gonjaCfg, shiftedLoader, env)
	return template, err
}

func ExecuteJinjaTemplate(templateSource string, templates map[string]string, ctx *exec.Context, flags JinjaFlags) (string, bool) {
	tpl, err := createJinjaTemplate(templateSource, templates, flags)
	if err != nil {
		log.Warn("Invalid jinja template generated from %v: %v", util.GetCaller(), err)
		return "", false
	} else {
		output, err := tpl.ExecuteToString(ctx)
		if err != nil {
			log.Warn("Failure during jinja exec generated from %v: %v", util.GetCaller(), err)
			return "", false
		} else {
			return output, true
		}
	}
}

func RunJinjaInvocation() {
	template, err := createJinjaTemplate(`
{{ None | option("--an-optional-option") }}   -> ""
{{ 42 | option("--count") }}                  -> "--count '42'"
{{ 42 | option("--count=") }}                 -> "'--count=42'"
{{ 42 | option("--count", false) }}           -> "'--count42'"
{{ 42 | option("--count=", true) }}           -> "--count= '42'"
{{ "/path/with spaces" | option("--file") }}  -> "--file '/path/with spaces'"

		{% if nested.property.bool %}
			{{ nested.property.a }}
		{% else %}
			{{ nested.property.b }}
		{% endif %}

		{{ script("fp") }}
		{{ unscript("fp") }}
	`, nil, 0)

	if err != nil {
		panic(err)
	}

	data, err := template.ExecuteToString(exec.NewContext(map[string]any{
		"nested": map[string]any{
			"property": map[string]any{
				"bool": false,
				"a":    "This is a",
				"b":    "This is the b property",
			},
		},
	}))

	fmt.Printf("%s\n", data)
	fmt.Printf("Error is %s\n", err)
}
