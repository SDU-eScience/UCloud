package inference

import (
	"bytes"
	"encoding/base64"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"

	_ "golang.org/x/image/webp"
)

const (
	inferenceMaxImageReadBytes = 64 << 20
	inferenceMaxImagePixels    = inferenceMaxImageDimension * inferenceMaxImageDimension
	inferenceMaxImageCount     = 8
)

func inferenceMeasureImageElements(
	elements []InferenceImageGenerationResponseEl,
	model InferenceModel,
	requestSize string,
	responseSize string,
) ([]InferenceImageGenerationResponseEl, int, int64, bool, *util.HttpError) {
	if len(elements) > inferenceMaxImageCount {
		return nil, 0, 0, false, util.HttpErr(http.StatusBadGateway, "too many images in response")
	}

	result := make([]InferenceImageGenerationResponseEl, 0, len(elements))
	imageCount := 0
	var imagePixels int64
	estimated := false

	for _, element := range elements {
		measured, pixels, elementEstimated, httpErr := inferenceMeasureImageElement(element, model, requestSize, responseSize)
		if httpErr != nil {
			return nil, 0, 0, false, httpErr
		}
		result = append(result, measured)
		imageCount++
		imagePixels = inferenceUsageAdd(imagePixels, pixels)
		estimated = estimated || elementEstimated
	}

	return result, imageCount, imagePixels, estimated, nil
}

func inferenceMeasureImageElement(
	element InferenceImageGenerationResponseEl,
	model InferenceModel,
	requestSize string,
	responseSize string,
) (InferenceImageGenerationResponseEl, int64, bool, *util.HttpError) {
	if element.URL.Present {
		raw := strings.TrimSpace(element.URL.Value)
		if parsed, err := url.Parse(raw); err == nil && strings.EqualFold(parsed.Scheme, "file") {
			data, httpErr := inferenceImageReadFileURL(parsed, model)
			if httpErr != nil {
				return element, 0, false, httpErr
			}
			pixels, ok := inferenceImagePixelCount(data)
			if !ok {
				return element, 0, false, util.HttpErr(http.StatusBadGateway, "invalid image response")
			}
			element.B64JSON.Set(base64.StdEncoding.EncodeToString(data))
			element.URL.Clear()
			return element, pixels, false, nil
		}
	}

	if element.B64JSON.Present {
		data, httpErr := inferenceImageBytesFromBase64OrDataURL(element.B64JSON.Value)
		if httpErr != nil {
			return element, 0, false, httpErr
		}
		pixels, ok := inferenceImagePixelCount(data)
		if !ok {
			return element, 0, false, util.HttpErr(http.StatusBadGateway, "invalid image response")
		}
		return element, pixels, false, nil
	}

	if element.URL.Present {
		raw := strings.TrimSpace(element.URL.Value)
		if data, ok := inferenceImageDataURLBytes(raw); ok {
			pixels, ok := inferenceImagePixelCount(data)
			if !ok {
				return element, 0, false, util.HttpErr(http.StatusBadGateway, "invalid image response")
			}
			return element, pixels, false, nil
		}

		parsed, err := url.Parse(raw)
		if err != nil {
			return element, 0, false, util.HttpErr(http.StatusBadGateway, "invalid image response")
		}
		switch parsed.Scheme {
		case "http", "https":
			pixels, exact := inferenceImageSizePixels(responseSize)
			if !exact {
				pixels, exact = inferenceImageSizePixels(requestSize)
			}
			if !exact {
				pixels = int64(inferenceDefaultImageDimension) * int64(inferenceDefaultImageDimension)
			}
			return element, pixels, !exact, nil
		default:
			return element, 0, false, util.HttpErr(http.StatusBadGateway, "invalid image response")
		}
	}

	return element, 0, false, util.HttpErr(http.StatusBadGateway, "invalid image response")
}

func inferenceImageBytesFromBase64OrDataURL(raw string) ([]byte, *util.HttpError) {
	if data, ok := inferenceImageDataURLBytes(raw); ok {
		return data, nil
	}
	data, err := base64.StdEncoding.DecodeString(strings.TrimSpace(raw))
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}
	return data, nil
}

func inferenceImageDataURLBytes(raw string) ([]byte, bool) {
	raw = strings.TrimSpace(raw)
	if !strings.HasPrefix(raw, "data:") {
		return nil, false
	}
	comma := strings.IndexByte(raw, ',')
	if comma < 0 {
		return nil, false
	}
	metadata := raw[len("data:"):comma]
	if !strings.Contains(metadata, ";base64") {
		return nil, false
	}
	data, err := base64.StdEncoding.DecodeString(raw[comma+1:])
	if err != nil {
		return nil, false
	}
	return data, true
}

func inferenceImagePixelCount(data []byte) (int64, bool) {
	config, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil || config.Width <= 0 || config.Height <= 0 ||
		config.Width > inferenceMaxImageDimension || config.Height > inferenceMaxImageDimension {
		return 0, false
	}
	pixels := int64(config.Width) * int64(config.Height)
	if pixels > inferenceMaxImagePixels {
		return 0, false
	}
	return pixels, true
}

func inferenceImageSizePixels(raw string) (int64, bool) {
	raw = strings.TrimSpace(raw)
	if raw == "" || strings.EqualFold(raw, "auto") {
		return 0, false
	}
	parts := strings.Split(strings.ToLower(raw), "x")
	if len(parts) != 2 {
		return 0, false
	}
	width, widthErr := strconv.Atoi(parts[0])
	height, heightErr := strconv.Atoi(parts[1])
	if widthErr != nil || heightErr != nil || width <= 0 || height <= 0 ||
		width > inferenceMaxImageDimension || height > inferenceMaxImageDimension {
		return 0, false
	}
	return int64(width) * int64(height), true
}

func inferenceImageReadFileURL(parsed *url.URL, model InferenceModel) ([]byte, *util.HttpError) {
	rootPath := strings.TrimSpace(model.FileUrlRoot)
	if rootPath == "" {
		return nil, util.HttpErr(http.StatusBadGateway, "file URL output is not configured")
	}
	rootPath = filepath.Clean(rootPath)
	if !filepath.IsAbs(rootPath) {
		return nil, util.HttpErr(http.StatusBadGateway, "file URL output is not configured")
	}
	if parsed.Host != "" && parsed.Host != "localhost" {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}
	if parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}

	filePath := filepath.Clean(parsed.Path)
	if !filepath.IsAbs(filePath) || !inferenceImagePathWithin(rootPath, filePath) {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}
	relativePath, err := filepath.Rel(rootPath, filePath)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}

	root, err := os.OpenRoot(rootPath)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}
	defer util.SilentClose(root)

	linkInfo, err := root.Lstat(relativePath)
	if err != nil || linkInfo.Mode()&os.ModeSymlink != 0 {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}

	file, err := root.Open(relativePath)
	if err != nil {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}
	defer util.SilentClose(file)

	info, err := file.Stat()
	if err != nil || !info.Mode().IsRegular() || info.Size() > inferenceMaxImageReadBytes {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}

	data, err := io.ReadAll(io.LimitReader(file, inferenceMaxImageReadBytes+1))
	if err != nil || len(data) > inferenceMaxImageReadBytes {
		return nil, util.HttpErr(http.StatusBadGateway, "invalid image response")
	}
	if err := root.Remove(relativePath); err != nil && !os.IsNotExist(err) {
		log.Warn("Could not delete inference image file: path=%s err=%v", filePath, err)
	}
	return data, nil
}

func inferenceImagePathWithin(root string, candidate string) bool {
	relative, err := filepath.Rel(filepath.Clean(root), filepath.Clean(candidate))
	if err != nil {
		return false
	}
	return relative != "." &&
		relative != ".." &&
		!strings.HasPrefix(relative, ".."+string(os.PathSeparator)) &&
		!filepath.IsAbs(relative)
}
