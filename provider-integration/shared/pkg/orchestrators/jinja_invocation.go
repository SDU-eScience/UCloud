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
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
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

	matchOrSearch := func(isMatch bool) func(context *exec.Context, value *exec.Value, args *exec.VarArgs) (bool, error) {
		return func(context *exec.Context, value *exec.Value, args *exec.VarArgs) (bool, error) {
			if !value.IsString() {
				return false, fmt.Errorf("input value must be a string")
			}

			if len(args.Args) != 1 {
				return false, fmt.Errorf("expected a pattern to match against")
			}

			ignoreCase := false
			ignoreCaseArg, hasIgnoreCase := args.KwArgs["ignorecase"]

			if hasIgnoreCase {
				if !ignoreCaseArg.IsBool() {
					return false, fmt.Errorf("ignorecase must be a boolean")
				}

				ignoreCase = ignoreCaseArg.Bool()
			}

			multiline := false
			multiLineArg, hasMultiLine := args.KwArgs["multiline"]
			if hasMultiLine {
				if !multiLineArg.IsBool() {
					return false, fmt.Errorf("multiline must be a boolean")
				}

				multiline = multiLineArg.Bool()
			}

			regexPrefix := ""
			regexSuffix := ""
			regexFlags := ""
			if ignoreCase {
				regexFlags += "i"
			}
			if multiline {
				regexFlags += "m"
			}
			if regexFlags != "" {
				regexFlags = "(?" + regexFlags + ")"
			}
			if isMatch {
				regexPrefix = "^"
			}
			if isMatch {
				regexSuffix = "$"
			}

			compiled, err := regexp.Compile(regexFlags + regexPrefix + args.Args[0].String() + regexSuffix)
			if err != nil {
				return false, fmt.Errorf("could not compile input regex: %s", err)
			}

			return compiled.MatchString(value.String()), nil
		}
	}

	tests := exec.NewTestSet(map[string]exec.TestFunction{
		"match":  matchOrSearch(true),
		"search": matchOrSearch(false),
	}).Update(builtins.Tests)

	env := &exec.Environment{
		Context:           exec.EmptyContext().Update(builtins.GlobalFunctions),
		Filters:           filters,
		Tests:             tests,
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

func ExecuteJinjaTemplate(templateSource string, templateSession any, templates TemplateInvocation, ctx *exec.Context, flags JinjaFlags) (string, error) {
	tpl, err := PrepareJinjaTemplate(templateSource, templateSession, templates, flags)
	if err != nil {
		log.Warn("Invalid jinja template generated from %v: %v", util.GetCaller(), err)
		return "", err
	} else {
		return ExecutePreparedJinjaTemplate(tpl, ctx, flags)
	}
}

func ExecutePreparedJinjaTemplate(tpl *exec.Template, ctx *exec.Context, flags JinjaFlags) (string, error) {
	output, err := tpl.ExecuteToString(ctx)
	if err != nil {
		log.Warn("Failure during jinja exec generated from %v: %v", util.GetCaller(), err)
		return "", err
	} else {
		return output, nil
	}
}
