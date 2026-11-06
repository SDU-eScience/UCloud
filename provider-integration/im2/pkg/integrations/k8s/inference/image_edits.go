package inference

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"image"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"strconv"
	"strings"

	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/util"
)

const (
	inferenceMaxImageEditRequestBytes = 64 << 20
	inferenceMaxImageEditFileBytes    = 20 << 20
	inferenceMaxImageDimension        = 8192
)

type InferenceImageEditFile struct {
	Name        string
	ContentType string
	Data        []byte
}

func InferenceImageEditParseRequest(w http.ResponseWriter, r *http.Request) (InferenceImageGenerationRequest, *util.HttpError) {
	if r.ContentLength > inferenceMaxImageEditRequestBytes {
		return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
	}
	r.Body = http.MaxBytesReader(w, r.Body, inferenceMaxImageEditRequestBytes)
	if err := r.ParseMultipartForm(8 << 20); err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusRequestEntityTooLarge, "request body too large")
		}
		return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	if r.MultipartForm != nil {
		defer r.MultipartForm.RemoveAll()
	}

	imageFile, imageHeader, err := r.FormFile("image")
	if err != nil {
		return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusBadRequest, "image is required")
	}
	defer util.SilentClose(imageFile)
	imageData, httpErr := inferenceReadImageEditFile(imageFile, imageHeader)
	if httpErr != nil {
		return InferenceImageGenerationRequest{}, httpErr
	}

	request := InferenceImageGenerationRequest{
		Prompt: r.FormValue("prompt"),
		Image:  &imageData,
	}
	if strings.TrimSpace(request.Prompt) == "" {
		return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusBadRequest, "prompt is required")
	}

	maskFile, maskHeader, maskErr := r.FormFile("mask")
	if maskErr == nil {
		defer util.SilentClose(maskFile)
		maskData, httpErr := inferenceReadImageEditFile(maskFile, maskHeader)
		if httpErr != nil {
			return InferenceImageGenerationRequest{}, httpErr
		}
		request.Mask = &maskData
	} else if !errors.Is(maskErr, http.ErrMissingFile) {
		return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusBadRequest, "invalid mask")
	}

	if v := strings.TrimSpace(r.FormValue("model")); v != "" {
		request.Model.Set(v)
	}
	if httpErr := inferenceParseImageEditInt(r, "n", &request.N); httpErr != nil {
		return InferenceImageGenerationRequest{}, httpErr
	}
	if httpErr := inferenceParseImageEditInt(r, "partial_images", &request.PartialImages); httpErr != nil {
		return InferenceImageGenerationRequest{}, httpErr
	}
	if httpErr := inferenceParseImageEditInt(r, "output_compression", &request.OutputCompression); httpErr != nil {
		return InferenceImageGenerationRequest{}, httpErr
	}
	if v := strings.TrimSpace(r.FormValue("size")); v != "" {
		if httpErr := inferenceValidateImageEditSize(v); httpErr != nil {
			return InferenceImageGenerationRequest{}, httpErr
		}
		request.Size.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("response_format")); v != "" {
		if v != "url" && v != "b64_json" {
			return InferenceImageGenerationRequest{}, util.HttpErr(http.StatusBadRequest, "unsupported response format")
		}
		request.ResponseFormat.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("stream")); v != "" {
		request.Stream.Set(parseFormBool(v))
	}
	if v := strings.TrimSpace(r.FormValue("quality")); v != "" {
		request.Quality.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("background")); v != "" {
		request.Background.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("output_format")); v != "" {
		request.OutputFormat.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("moderation")); v != "" {
		request.Moderation.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("style")); v != "" {
		request.Style.Set(v)
	}
	if v := strings.TrimSpace(r.FormValue("user")); v != "" {
		request.User.Set(v)
	}

	if httpErr := inferenceValidateImageRequest(request); httpErr != nil {
		return InferenceImageGenerationRequest{}, httpErr
	}
	return request, nil
}

func inferenceReadImageEditFile(file multipart.File, header *multipart.FileHeader) (InferenceImageEditFile, *util.HttpError) {
	data, err := io.ReadAll(io.LimitReader(file, inferenceMaxImageEditFileBytes+1))
	if err != nil {
		return InferenceImageEditFile{}, util.HttpErr(http.StatusBadRequest, "invalid image")
	}
	if len(data) > inferenceMaxImageEditFileBytes {
		return InferenceImageEditFile{}, util.HttpErr(http.StatusRequestEntityTooLarge, "image is too large")
	}

	contentType := http.DetectContentType(data)
	switch contentType {
	case "image/png", "image/jpeg", "image/webp":
	default:
		return InferenceImageEditFile{}, util.HttpErr(http.StatusBadRequest, "unsupported image type")
	}

	config, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return InferenceImageEditFile{}, util.HttpErr(http.StatusBadRequest, "invalid image")
	}
	if config.Width <= 0 || config.Height <= 0 ||
		config.Width > inferenceMaxImageDimension || config.Height > inferenceMaxImageDimension {
		return InferenceImageEditFile{}, util.HttpErr(http.StatusBadRequest, "invalid image dimensions")
	}

	return InferenceImageEditFile{
		Name:        header.Filename,
		ContentType: contentType,
		Data:        data,
	}, nil
}

func inferenceParseImageEditInt(r *http.Request, key string, dst *util.Option[int]) *util.HttpError {
	raw := strings.TrimSpace(r.FormValue(key))
	if raw == "" {
		return nil
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid %s", key)
	}
	dst.Set(value)
	return nil
}

func inferenceValidateImageEditSize(raw string) *util.HttpError {
	if strings.EqualFold(raw, "auto") {
		return nil
	}
	parts := strings.Split(strings.ToLower(raw), "x")
	if len(parts) != 2 {
		return util.HttpErr(http.StatusBadRequest, "invalid image size")
	}
	width, widthErr := strconv.Atoi(parts[0])
	height, heightErr := strconv.Atoi(parts[1])
	if widthErr != nil || heightErr != nil || width <= 0 || height <= 0 ||
		width > inferenceMaxImageDimension || height > inferenceMaxImageDimension {
		return util.HttpErr(http.StatusBadRequest, "invalid image size")
	}
	return nil
}

func inferenceImageEditUsesJSONReferences() bool {
	return shared.ServiceConfig.Compute.Inference.Provider == cfg.KubernetesInferenceProviderDynamo
}

func inferenceBuildImageEditJSON(request InferenceImageGenerationRequest) ([]byte, *util.HttpError) {
	if request.Image == nil {
		return nil, util.HttpErr(http.StatusBadRequest, "image is required")
	}
	if request.Mask != nil {
		return nil, util.HttpErr(http.StatusBadRequest, "image masks are not supported")
	}
	request.InputReference = util.OptValue("data:" + request.Image.ContentType + ";base64," +
		base64.StdEncoding.EncodeToString(request.Image.Data))
	request.Image = nil
	request.NumInferenceSteps.Clear()
	body, err := json.Marshal(request)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	return body, nil
}

func inferenceBuildImageEditMultipart(request InferenceImageGenerationRequest) ([]byte, string, *util.HttpError) {
	if request.Image == nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "image is required")
	}
	if request.Mask != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "image masks are not supported")
	}
	if request.PartialImages.Present {
		return nil, "", util.HttpErr(http.StatusBadRequest, "partial images are not supported for image edits")
	}
	if request.OutputCompression.Present {
		return nil, "", util.HttpErr(http.StatusBadRequest, "output compression is not supported for image edits")
	}
	if request.Stream.GetOrDefault(false) {
		return nil, "", util.HttpErr(http.StatusBadRequest, "streaming is not supported for image edits")
	}

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	if err := inferenceWriteImageEditPart(writer, "image", *request.Image); err != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}

	writeField := func(key string, value string) {
		if value != "" {
			_ = writer.WriteField(key, value)
		}
	}
	writeInt := func(key string, value util.Option[int]) {
		if value.Present {
			writeField(key, strconv.Itoa(value.Value))
		}
	}

	writeField("prompt", request.Prompt)
	writeField("model", request.Model.GetOrDefault(""))
	writeInt("n", request.N)
	writeField("size", request.Size.GetOrDefault(""))
	writeField("response_format", request.ResponseFormat.GetOrDefault(""))
	writeField("quality", request.Quality.GetOrDefault(""))
	writeField("background", request.Background.GetOrDefault(""))
	writeField("output_format", request.OutputFormat.GetOrDefault(""))
	writeField("moderation", request.Moderation.GetOrDefault(""))
	writeField("style", request.Style.GetOrDefault(""))
	writeField("user", request.User.GetOrDefault(""))
	if request.NvExt != nil {
		if nvExt, err := json.Marshal(request.NvExt); err == nil {
			writeField("nvext", string(nvExt))
		}
	}

	if err := writer.Close(); err != nil {
		return nil, "", util.HttpErr(http.StatusBadRequest, "invalid request")
	}
	return buf.Bytes(), writer.FormDataContentType(), nil
}

func inferenceWriteImageEditPart(writer *multipart.Writer, field string, file InferenceImageEditFile) error {
	filename := file.Name
	if filename == "" {
		filename = field
	}
	filename = strings.ReplaceAll(filename, "\r", "")
	filename = strings.ReplaceAll(filename, "\n", "")
	filename = strings.ReplaceAll(filename, `\`, `\\`)
	filename = strings.ReplaceAll(filename, `"`, `\"`)

	header := make(textproto.MIMEHeader)
	header.Set("Content-Disposition", fmt.Sprintf(`form-data; name="%s"; filename="%s"`, field, filename))
	header.Set("Content-Type", file.ContentType)
	part, err := writer.CreatePart(header)
	if err != nil {
		return err
	}
	_, err = part.Write(file.Data)
	return err
}
