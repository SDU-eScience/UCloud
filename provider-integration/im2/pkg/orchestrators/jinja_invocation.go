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
	JinjaFlagsNoPreProcess
)

var (
	templateRegex    = regexp.MustCompile("{- ([^-{}()]*)(\\(([^)]*)\\))? -}")
	templateArgRegex = regexp.MustCompile("\"([^\"]*)\"")
)

type TemplateInvocation = func(session any, fn string, args []string) string

func PreprocessJinjaTemplate(source string, templateSession any, templates TemplateInvocation) string {
	return templateRegex.ReplaceAllStringFunc(source, func(s string) string {
		fn := ""
		var args []string

		matches := templateRegex.FindStringSubmatch(s)
		if len(matches) == 4 {
			fn = matches[1]
			rawArgs := matches[2]
			innerMatches := templateArgRegex.FindAllStringSubmatch(rawArgs, -1)
			for _, m := range innerMatches {
				if len(m) == 2 {
					args = append(args, m[1])
				}
			}

			return templates(templateSession, fn, args)
		}

		return s
	})
}

func PrepareJinjaTemplate(source string, templateSession any, templates TemplateInvocation, flags JinjaFlags) (*exec.Template, error) {
	if cfg.Mode != cfg.ServerModeUser {
		return nil, fmt.Errorf("this function is only implemented for user mode")
	}

	if flags&JinjaFlagsNoPreProcess == 0 {
		source = PreprocessJinjaTemplate(source, templateSession, templates)
	}

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
		Methods:           builtins.Methods,
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

func ExecuteJinjaTemplate(templateSource string, templateSession any, templates TemplateInvocation, ctx *exec.Context, flags JinjaFlags) (string, bool) {
	tpl, err := PrepareJinjaTemplate(templateSource, templateSession, templates, flags)
	if err != nil {
		log.Warn("Invalid jinja template generated from %v: %v", util.GetCaller(), err)
		return "", false
	} else {
		return ExecutePreparedJinjaTemplate(tpl, ctx, flags)
	}
}

func ExecutePreparedJinjaTemplate(tpl *exec.Template, ctx *exec.Context, flags JinjaFlags) (string, bool) {
	output, err := tpl.ExecuteToString(ctx)
	if err != nil {
		log.Warn("Failure during jinja exec generated from %v: %v", util.GetCaller(), err)
		return "", false
	} else {
		return output, true
	}
}
