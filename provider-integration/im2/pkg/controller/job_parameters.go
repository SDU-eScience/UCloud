package controller

import (
	"encoding/json"
	"fmt"
	"slices"
	"strings"

	"ucloud.dk/gonja/v2/exec"
	"ucloud.dk/shared/pkg/log"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

// NOTE(Dan): This is slightly different from how escapeBash works in the Kotlin version since this also automatically
// wraps it in single quotes. This is how it was used in all cases anyway, so this makes it slightly simpler to use.

func EscapeBash(s string) string {
	builder := &strings.Builder{}
	builder.WriteRune('\'')
	for _, c := range []rune(s) {
		if c == '\'' {
			builder.WriteString("'\"'\"'")
		} else {
			builder.WriteRune(c)
		}
	}
	builder.WriteRune('\'')
	return builder.String()
}

type ParamAndValue struct {
	Parameter orcapi.ApplicationParameter
	Value     orcapi.AppParameterValue
}

type JobArgBuilder func(value ParamAndValue) string

func JobDefaultArgBuilder(fileMapper func(ucloudPath string) string) JobArgBuilder {
	return func(pv ParamAndValue) string {
		param := pv.Parameter
		value := pv.Value

		switch param.Type {
		case orcapi.ApplicationParameterTypeTextArea:
			fallthrough
		case orcapi.ApplicationParameterTypeText:
			return fmt.Sprint(value.Value)

		case orcapi.ApplicationParameterTypeFloatingPoint:
			fallthrough
		case orcapi.ApplicationParameterTypeInteger:
			return fmt.Sprint(value.Value)

		case orcapi.ApplicationParameterTypeBoolean:
			b := value.Value.(bool)
			res := ""
			if b {
				res = param.TrueValue
			} else {
				res = param.FalseValue
			}

			if res == "" {
				if b {
					return "true"
				} else {
					return "false"
				}
			} else {
				return res
			}

		case orcapi.ApplicationParameterTypeEnumeration:
			stringified := fmt.Sprint(value.Value)

			for _, opt := range param.Options {
				if opt.Name == stringified {
					return opt.Value
				}
			}
			return stringified

		case orcapi.ApplicationParameterTypeInputFile:
			fallthrough
		case orcapi.ApplicationParameterTypeInputDirectory:
			return fileMapper(value.Path)

		case orcapi.ApplicationParameterTypePeer:
			return value.Hostname

		case orcapi.ApplicationParameterTypeLicenseServer:
			fallthrough
		case orcapi.ApplicationParameterTypeNetworkIp:
			fallthrough
		case orcapi.ApplicationParameterTypeIngress:
			return value.Id

		case orcapi.ApplicationParameterTypeWorkflow:
			return ""

		default:
			log.Warn("Unhandled value type: %v", param.Type)
			return ""
		}
	}
}

func JobBuildParameter(
	param orcapi.InvocationParameter,
	values map[string]ParamAndValue,
	environmentVariable bool,
	builder JobArgBuilder,
	jinjaCtx *exec.Context,
) []string {
	switch param.Type {
	case orcapi.InvocationParameterTypeJinja:
		if jinjaCtx == nil {
			return nil
		} else {
			flags := JinjaFlags(0)
			if environmentVariable {
				flags |= JinjaFlagsNoEscape
			}

			output, err := JinjaTemplateExecute(
				param.InvocationParameterJinja.Template,
				0,
				func(session any, fn string, args []string) string {
					return ""
				},
				jinjaCtx,
				flags,
			)
			if err != nil {
				return nil
			} else {
				return []string{output}
			}
		}

	case orcapi.InvocationParameterTypeWord:
		return []string{param.InvocationParameterWord.Word}

	case orcapi.InvocationParameterTypeEnv:
		if environmentVariable {
			return []string{fmt.Sprintf("$(%v)", param.InvocationParameterEnv.Variable)}
		} else {
			return []string{fmt.Sprintf("$%v", param.InvocationParameterEnv.Variable)}
		}

	case orcapi.InvocationParameterTypeVar:
		v := &param.InvocationParameterVar
		prefixGlobal := strings.Trim(v.PrefixGlobal, " ")
		suffixGlobal := strings.Trim(v.SuffixGlobal, " ")
		prefixVariable := strings.Trim(v.PrefixVariable, " ")
		suffixVariable := strings.Trim(v.SuffixVariable, " ")

		relevantValues := make(map[string]ParamAndValue)
		for key, value := range values {
			if slices.Contains(v.VariableNames, key) {
				relevantValues[key] = value
			}
		}

		// We assume that verification has already taken place. If we have no values it should mean that they are all
		// optional. We don't include anything (including prefixGlobal) if no values were given.
		if len(relevantValues) == 0 {
			return nil
		}

		var middlePart []string

		for _, variableName := range v.VariableNames {
			value, ok := relevantValues[variableName]
			if !ok {
				continue
			}

			mainArg := strings.Builder{}
			if len(prefixVariable) > 0 {
				if v.IsPrefixVariablePartOfArg {
					mainArg.WriteString(prefixVariable)
				} else {
					middlePart = append(middlePart, prefixVariable)
				}
			}

			mainArg.WriteString(builder(value))

			if v.IsSuffixVariablePartOfArg {
				mainArg.WriteString(suffixVariable)
				middlePart = append(middlePart, mainArg.String())
			} else {
				middlePart = append(middlePart, mainArg.String())
				if len(suffixVariable) > 0 {
					middlePart = append(middlePart, suffixVariable)
				}
			}
		}

		if prefixGlobal == "" && suffixGlobal == "" {
			return middlePart
		}

		var result []string
		if len(prefixGlobal) > 0 {
			result = append(result, prefixGlobal)
		}

		result = append(result, middlePart...)

		if len(suffixGlobal) > 0 {
			result = append(result, suffixGlobal)
		}

		for i := 0; i < len(result); i++ {
			result[i] = strings.TrimSpace(result[i])
		}

		return result

	case orcapi.InvocationParameterTypeBoolFlag:
		f := &param.InvocationParameterBoolFlag
		value, ok := values[f.VariableName]
		if !ok {
			return nil
		}

		asBool, ok := value.Value.Value.(bool)
		if !ok || !asBool {
			return nil
		}

		return []string{strings.TrimSpace(f.Flag)}

	default:
		log.Warn("Unhandled value type: %v", param.Type)
		return nil
	}
}

func JobVerifyParameterType(param *orcapi.ApplicationParameter, value *orcapi.AppParameterValue) bool {
	switch param.Type {
	case orcapi.ApplicationParameterTypeInputDirectory:
		fallthrough
	case orcapi.ApplicationParameterTypeInputFile:
		if value.Type != orcapi.AppParameterValueTypeFile {
			return false
		}

	case orcapi.ApplicationParameterTypeBoolean:
		if value.Type != orcapi.AppParameterValueTypeBoolean {
			return false
		}

	case orcapi.ApplicationParameterTypeFloatingPoint:
		if value.Type != orcapi.AppParameterValueTypeFloatingPoint {
			return false
		}

	case orcapi.ApplicationParameterTypeIngress:
		if value.Type != orcapi.AppParameterValueTypeIngress {
			return false
		}

	case orcapi.ApplicationParameterTypeInteger:
		if value.Type != orcapi.AppParameterValueTypeInteger {
			return false
		}

	case orcapi.ApplicationParameterTypeLicenseServer:
		if value.Type != orcapi.AppParameterValueTypeLicense {
			return false
		}

	case orcapi.ApplicationParameterTypeNetworkIp:
		if value.Type != orcapi.AppParameterValueTypeNetwork {
			return false
		}

	case orcapi.ApplicationParameterTypePeer:
		if value.Type != orcapi.AppParameterValueTypePeer {
			return false
		}

	case orcapi.ApplicationParameterTypeText:
		if value.Type != orcapi.AppParameterValueTypeText {
			return false
		}

	case orcapi.ApplicationParameterTypeEnumeration:
		if value.Type != orcapi.AppParameterValueTypeText {
			return false
		}

	case orcapi.ApplicationParameterTypeTextArea:
		if value.Type != orcapi.AppParameterValueTypeText {
			return false
		}

	case orcapi.ApplicationParameterTypeModuleList:
		if value.Type != orcapi.AppParameterValueTypeModuleList {
			return false
		}
	}
	return true
}

func JobFindParamAndValues(
	job *orcapi.Job,
	application *orcapi.ApplicationInvocationDescription,
	dynamicParameters []orcapi.ApplicationParameter,
) map[string]ParamAndValue {
	parameters := make(map[string]ParamAndValue)

	allParameters := application.Parameters
	for _, value := range job.Specification.Parameters {
		if value.Type == orcapi.AppParameterValueTypeWorkflow {
			inputs := value.Specification.Inputs
			for _, input := range inputs {
				allParameters = append(allParameters, input)
			}
		}
	}

	for _, param := range dynamicParameters {
		allParameters = append(allParameters, param)
	}

	for _, param := range allParameters {
		if param.DefaultValue == nil {
			continue
		}

		value, ok := jobReadDefaultValue(param.Type, param.DefaultValue)
		if !ok {
			continue
		}

		if !JobVerifyParameterType(&param, &value) {
			continue
		}

		parameters[param.Name] = ParamAndValue{
			Parameter: param,
			Value:     value,
		}
	}

	for paramName, value := range job.Specification.Parameters {
		if strings.HasPrefix(paramName, "_injected_") {
			parameters[paramName] = ParamAndValue{
				Value: value,
			}
		} else {
			var parameter util.Option[orcapi.ApplicationParameter]
			for _, jobParam := range allParameters {
				if jobParam.Name == paramName {
					parameter.Set(jobParam)
					break
				}
			}

			if !parameter.IsSet() {
				continue
			}

			param := parameter.Get()
			if !JobVerifyParameterType(&param, &value) {
				continue
			}

			parameters[paramName] = ParamAndValue{
				Parameter: param,
				Value:     value,
			}
		}
	}
	return parameters
}

func jobReadDefaultValue(t orcapi.ApplicationParameterType, input json.RawMessage) (orcapi.AppParameterValue, bool) {
	if string(input) == "null" {
		return orcapi.AppParameterValue{}, false
	}

	switch t {
	case orcapi.ApplicationParameterTypeBoolean:
		v, ok := jobGenericDefaultParse[bool](input)
		if ok {
			return orcapi.AppParameterValueBoolean(v), true
		}

	case orcapi.ApplicationParameterTypeInteger:
		v, ok := jobGenericDefaultParse[int64](input)
		if ok {
			return orcapi.AppParameterValueInteger(v), true
		}

	case orcapi.ApplicationParameterTypeFloatingPoint:
		v, ok := jobGenericDefaultParse[float64](input)
		if ok {
			return orcapi.AppParameterValueFloatingPoint(v), true
		}

	case orcapi.ApplicationParameterTypeText,
		orcapi.ApplicationParameterTypeTextArea,
		orcapi.ApplicationParameterTypeEnumeration:
		v, ok := jobGenericDefaultParse[string](input)
		if ok {
			return orcapi.AppParameterValueText(v), true
		}
	}

	return orcapi.AppParameterValue{}, false
}

func jobGenericDefaultParse[T any](input json.RawMessage) (T, bool) {
	var asDirect T
	err := json.Unmarshal(input, &asDirect)
	if err == nil {
		return asDirect, true
	}

	var asWrapped struct{ Value T }
	err = json.Unmarshal(input, &asWrapped)
	if err == nil {
		return asWrapped.Value, true
	} else {
		return asDirect, false
	}
}
