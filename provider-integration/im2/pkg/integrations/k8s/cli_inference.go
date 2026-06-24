package k8s

import (
	"encoding/json"
	"flag"
	"fmt"
	"net/http"
	"os"
	"slices"
	"strings"

	"ucloud.dk/pkg/ipc"
	"ucloud.dk/shared/pkg/cli"
	"ucloud.dk/shared/pkg/termio"
	"ucloud.dk/shared/pkg/util"
)

func InferenceCli(args []string) {
	if os.Getuid() != 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "This command must be run as root!")
		os.Exit(1)
	}

	if len(args) == 0 || cli.IsHelpCommand(args[0]) {
		inferenceCliHelp()
		return
	}

	if args[0] != "models" && args[0] != "model" {
		inferenceCliHelp()
		return
	}

	command := ""
	if len(args) >= 2 {
		command = args[1]
	}

	switch {
	case cli.IsListCommand(command):
		inferenceCliModelsList(args[2:])
	case command == "update":
		inferenceCliModelsUpdate(args[2:])
	case cli.IsDeleteCommand(command):
		inferenceCliModelsRemove(args[2:])
	default:
		inferenceCliHelp()
	}
}

func inferenceCliHelp() {
	f := termio.Frame{}
	f.AppendTitle("Inference help")
	f.AppendField("models ls", "List inference model catalog entries")
	f.AppendSeparator()
	f.AppendField("models update <name>", "Update an inference model catalog entry")
	f.AppendField("  --name <name>", "Rename the model. Only allowed for non-public models.")
	f.AppendField("  --title <title>", "Set the display title")
	f.AppendField("  --capabilities <list>", "Comma-separated list: TextGeneration, TextToImage, SpeechToText")
	f.AppendField("  --price-cached <n>", "Cached input multiplier in fixed-point thousandths")
	f.AppendField("  --price-input <n>", "Input multiplier in fixed-point thousandths")
	f.AppendField("  --price-output <n>", "Output multiplier in fixed-point thousandths")
	f.AppendField("  --public <bool>", "Set public availability")
	f.AppendField("  --available-to <list>", "Comma-separated project IDs")
	f.AppendField("  --base-path <url>", "Set endpoint base path")
	f.AppendField("  --backend-model-name <name>", "Set backend model name")
	f.AppendSeparator()
	f.AppendField("models rm <name>", "Remove an inference model catalog entry")
	f.Print()
}

func inferenceCliModelsList(args []string) {
	fs := flag.NewFlagSet("inference models ls", flag.ExitOnError)
	jsonOutput := fs.Bool("json", false, "Print JSON output")
	_ = fs.Parse(args)

	models, err := k8sCliInferenceModelsList.Invoke(util.Empty{})
	cli.HandleError("listing inference models", err)

	if *jsonOutput {
		data, err := json.MarshalIndent(models, "", "  ")
		cli.HandleError("encoding output", err)
		termio.WriteLine("%s", string(data))
		return
	}

	t := termio.Table{}
	t.AppendHeader("Name")
	t.AppendHeader("Title")
	t.AppendHeader("Capabilities")
	t.AppendHeaderEx("Cached", termio.TableHeaderAlignRight)
	t.AppendHeaderEx("Input", termio.TableHeaderAlignRight)
	t.AppendHeaderEx("Output", termio.TableHeaderAlignRight)
	t.AppendHeader("Endpoint")
	t.AppendHeader("Availability")

	for _, model := range models {
		t.Cell("%s", model.Name)
		t.Cell("%s", model.Title)
		t.Cell("%s", inferenceCliFormatCapabilities(model.Capabilities))
		t.Cell("%d", model.PriceMultiplier.CachedInput)
		t.Cell("%d", model.PriceMultiplier.Input)
		t.Cell("%d", model.PriceMultiplier.Output)
		t.Cell("%s -> %s", model.Endpoint.BasePath, model.Endpoint.BackendModelName)
		t.Cell("%s", inferenceCliFormatAvailability(model.Availability))
	}

	t.Print()
}

func inferenceCliModelsUpdate(args []string) {
	if len(args) == 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: model name")
		os.Exit(1)
	}

	req := k8sCliInferenceModelsUpdateRequest{Name: args[0]}
	var capabilitiesRaw string
	var availableToRaw string
	fs := flag.NewFlagSet("inference models update", flag.ExitOnError)
	fs.StringVar(&req.NewName, "name", "", "Rename the model")
	fs.StringVar(&req.Title, "title", "", "Set the display title")
	fs.StringVar(&capabilitiesRaw, "capabilities", "", "Comma-separated capability list")
	fs.IntVar(&req.PriceCachedInput, "price-cached", 0, "Cached input multiplier")
	fs.IntVar(&req.PriceInput, "price-input", 0, "Input multiplier")
	fs.IntVar(&req.PriceOutput, "price-output", 0, "Output multiplier")
	fs.BoolVar(&req.Public, "public", false, "Set public availability")
	fs.StringVar(&availableToRaw, "available-to", "", "Comma-separated project IDs")
	fs.StringVar(&req.BasePath, "base-path", "", "Endpoint base path")
	fs.StringVar(&req.BackendModelName, "backend-model-name", "", "Backend model name")
	_ = fs.Parse(args[1:])
	if fs.NArg() != 0 {
		cli.HandleError("parsing arguments", fmt.Errorf("unexpected argument %q", fs.Arg(0)))
	}

	fs.Visit(func(f *flag.Flag) {
		req.Set = append(req.Set, f.Name)
	})
	if slices.Contains(req.Set, "name") && strings.TrimSpace(req.NewName) == "" {
		cli.HandleError("validating name", fmt.Errorf("name cannot be empty"))
	}

	if slices.Contains(req.Set, "capabilities") {
		capabilities, err := inferenceCliParseCapabilities(capabilitiesRaw)
		cli.HandleError("parsing capabilities", err)
		req.Capabilities = capabilities
	}
	if slices.Contains(req.Set, "available-to") {
		availableTo, err := inferenceCliParseList(availableToRaw)
		cli.HandleError("parsing available-to", err)
		req.AvailableTo = availableTo
	}
	for _, priceFlag := range []struct {
		Name  string
		Value int
	}{
		{Name: "price-cached", Value: req.PriceCachedInput},
		{Name: "price-input", Value: req.PriceInput},
		{Name: "price-output", Value: req.PriceOutput},
	} {
		if slices.Contains(req.Set, priceFlag.Name) && priceFlag.Value < 0 {
			cli.HandleError("validating prices", fmt.Errorf("%s cannot be negative", priceFlag.Name))
		}
	}

	_, err := k8sCliInferenceModelsUpdate.Invoke(req)
	cli.HandleError("updating inference model", err)

	name := strings.TrimSpace(req.Name)
	if slices.Contains(req.Set, "name") && strings.TrimSpace(req.NewName) != "" {
		name = strings.TrimSpace(req.NewName)
	}
	termio.WriteStyledLine(termio.Bold, termio.Green, 0, "Updated inference model: %s", name)
}

func inferenceCliModelsRemove(args []string) {
	if len(args) == 0 {
		termio.WriteStyledLine(termio.Bold, termio.Red, 0, "Missing parameter: model name")
		os.Exit(1)
	}

	_, err := k8sCliInferenceModelsRemove.Invoke(k8sCliInferenceModelsRemoveRequest{Name: args[0]})
	cli.HandleError("removing inference model", err)
	termio.WriteStyledLine(termio.Bold, termio.Green, 0, "Removed inference model: %s", args[0])
}

func initInferenceCli() {
	k8sCliInferenceModelsList.Handler(func(r *ipc.Request[util.Empty]) ipc.Response[[]InferenceModel] {
		if r.Uid != 0 {
			return ipc.Response[[]InferenceModel]{StatusCode: http.StatusForbidden, ErrorMessage: "Must be run as root"}
		}

		return ipc.Response[[]InferenceModel]{StatusCode: http.StatusOK, Payload: InferenceModelList()}
	})

	k8sCliInferenceModelsUpdate.Handler(func(r *ipc.Request[k8sCliInferenceModelsUpdateRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{StatusCode: http.StatusForbidden, ErrorMessage: "Must be run as root"}
		}

		model, ok := InferenceCatalogModelByName(r.Payload.Name)
		if !ok {
			return ipc.Response[util.Empty]{StatusCode: http.StatusNotFound, ErrorMessage: "model not found"}
		}

		set := func(name string) bool { return slices.Contains(r.Payload.Set, name) }
		if set("title") {
			model.Title = r.Payload.Title
		}
		if set("capabilities") {
			model.Capabilities = slices.Clone(r.Payload.Capabilities)
		}
		if set("price-cached") {
			model.PriceMultiplier.CachedInput = r.Payload.PriceCachedInput
		}
		if set("price-input") {
			model.PriceMultiplier.Input = r.Payload.PriceInput
		}
		if set("price-output") {
			model.PriceMultiplier.Output = r.Payload.PriceOutput
		}
		if set("public") {
			model.Availability.Public = r.Payload.Public
		}
		if set("available-to") {
			model.Availability.AvailableTo = slices.Clone(r.Payload.AvailableTo)
		}
		if set("base-path") {
			model.Endpoint.BasePath = r.Payload.BasePath
		}
		if set("backend-model-name") {
			model.Endpoint.BackendModelName = r.Payload.BackendModelName
		}

		newName := strings.TrimSpace(r.Payload.NewName)
		if set("name") && newName != "" && newName != strings.TrimSpace(r.Payload.Name) {
			model.Name = newName
			if err := inferenceModelValidate(model); err != nil {
				return ipc.Response[util.Empty]{StatusCode: err.StatusCode, ErrorMessage: err.Why}
			}
			if err := InferenceModelRename(r.Payload.Name, newName); err != nil {
				return ipc.Response[util.Empty]{StatusCode: err.StatusCode, ErrorMessage: err.Why}
			}
		}

		if err := InferenceModelUpsert(model); err != nil {
			return ipc.Response[util.Empty]{StatusCode: err.StatusCode, ErrorMessage: err.Why}
		}

		return ipc.Response[util.Empty]{StatusCode: http.StatusOK}
	})

	k8sCliInferenceModelsRemove.Handler(func(r *ipc.Request[k8sCliInferenceModelsRemoveRequest]) ipc.Response[util.Empty] {
		if r.Uid != 0 {
			return ipc.Response[util.Empty]{StatusCode: http.StatusForbidden, ErrorMessage: "Must be run as root"}
		}

		if err := InferenceModelDelete(r.Payload.Name); err != nil {
			return ipc.Response[util.Empty]{StatusCode: err.StatusCode, ErrorMessage: err.Why}
		}

		return ipc.Response[util.Empty]{StatusCode: http.StatusOK}
	})
}

func inferenceCliParseCapabilities(raw string) ([]InferenceCapability, error) {
	items, err := inferenceCliParseList(raw)
	if err != nil {
		return nil, err
	}

	capabilities := make([]InferenceCapability, 0, len(items))
	for _, item := range items {
		capability := InferenceCapability(item)
		switch capability {
		case InferenceTextGeneration, InferenceTextToImage, InferenceSpeechToText:
			capabilities = append(capabilities, capability)
		default:
			return nil, fmt.Errorf("invalid capability %q", item)
		}
	}
	return capabilities, nil
}

func inferenceCliParseList(raw string) ([]string, error) {
	if raw == "" {
		return nil, nil
	}

	parts := strings.Split(raw, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" {
			return nil, fmt.Errorf("list contains an empty value")
		}
		result = append(result, part)
	}
	return result, nil
}

func inferenceCliFormatCapabilities(capabilities []InferenceCapability) string {
	parts := make([]string, 0, len(capabilities))
	for _, capability := range capabilities {
		parts = append(parts, string(capability))
	}
	return strings.Join(parts, ",")
}

func inferenceCliFormatAvailability(availability InferenceAvailability) string {
	if availability.Public {
		return "public"
	}
	if len(availability.AvailableTo) == 0 {
		return "private"
	}
	return "private: " + strings.Join(availability.AvailableTo, ",")
}

type k8sCliInferenceModelsUpdateRequest struct {
	Name             string
	Set              []string
	NewName          string
	Title            string
	Capabilities     []InferenceCapability
	PriceCachedInput int
	PriceInput       int
	PriceOutput      int
	Public           bool
	AvailableTo      []string
	BasePath         string
	BackendModelName string
}

type k8sCliInferenceModelsRemoveRequest struct {
	Name string
}

var (
	k8sCliInferenceModelsList   = ipc.NewCall[util.Empty, []InferenceModel]("cli.k8s.inference.models.list")
	k8sCliInferenceModelsUpdate = ipc.NewCall[k8sCliInferenceModelsUpdateRequest, util.Empty]("cli.k8s.inference.models.update")
	k8sCliInferenceModelsRemove = ipc.NewCall[k8sCliInferenceModelsRemoveRequest, util.Empty]("cli.k8s.inference.models.remove")
)
