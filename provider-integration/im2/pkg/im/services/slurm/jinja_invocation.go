package slurm

import (
	"bytes"
	"crypto/sha256"
	"fmt"
	"ucloud.dk/gonja/v2/builtins"
	controlStructures "ucloud.dk/gonja/v2/builtins/control_structures"
	gonjacfg "ucloud.dk/gonja/v2/config"
	"ucloud.dk/gonja/v2/exec"
	"ucloud.dk/gonja/v2/loaders"
)

func createTemplateFromString(source string) (*exec.Template, error) {
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
				return exec.AsValue(trueFlag)
			} else {
				return exec.AsValue(falseFlag)
			}
		},
	}).Update(builtins.Filters)

	_ = filters.Replace("filter", func(e *exec.Evaluator, in *exec.Value, params *exec.VarArgs) *exec.Value {
		fmt.Printf("Running the filter!\n")
		return in
	})

	env := &exec.Environment{
		Context:           exec.EmptyContext().Update(builtins.GlobalFunctions),
		Filters:           filters,
		Tests:             builtins.Tests,
		ControlStructures: controlStructures.Safe,
		Methods:           builtins.Methods, // TODO Consider these
	}

	cfg := gonjacfg.New()
	cfg.AutoEscape = true

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

	template, err := exec.NewTemplate(rootID, cfg, shiftedLoader, env)
	return template, err
}

func RunJinjaInvocation() {
	template, err := createTemplateFromString(`example-exe \
    --input {{ input }} \
    --output {{ output }} \
	--count {{ count + 1 }} \
    {{ a_flag | flag("--flag", "--not-flag") }}

	{% if a_flag %}
		Flag is true {{ a_flag }} !
		{{ input }}
	{% endif %}
	{% if not a_flag %}
		Flag is not true {{ a_flag }}
		{{ output }}
	{% endif %}

	{% raw %}
	The include statement has been turned off. You can test it by removing the raw lines.
	{% include "/etc/passwd" without context %}
	{% endraw %}

	{% if unknown == None %}
		Unknown is none
	{% else %}
		Unknown is {{unknown}}
	{% endif %}
`)

	if err != nil {
		panic(err)
	}

	data, err := template.ExecuteToString(exec.NewContext(map[string]any{
		"input":   "/path/to/input/file<script>alert('fie');</script>",
		"output":  "/path/to/output",
		"a_flag":  false,
		"count":   41,
		"unknown": "1231",
	}))

	fmt.Printf("%s\n", data)
	fmt.Printf("Error is %s\n", err)
}
