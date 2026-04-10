package k8s

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"net/http"
	"strings"
	"time"

	"ucloud.dk/shared/pkg/util"
)

func inferenceGenerateMockImageResponse(request InferenceImageGenerationRequest) (InferenceImageGenerationResponse, *util.HttpError) {
	if request.Prompt == "" {
		request.Prompt = "(empty prompt)"
	}

	count := request.N.GetOrDefault(0)
	if count <= 0 {
		count = 1
	}
	if count > 8 {
		count = 8
	}

	w, h := inferenceParseImageSize(request.Size.GetOrDefault(""))
	response := InferenceImageGenerationResponse{
		Created: time.Now().Unix(),
		Data:    make([]InferenceImageGenerationResponseEl, 0, count),
	}

	for i := 0; i < count; i++ {
		seed := fmt.Sprintf("%s#%d", request.Prompt, i)
		imageData, err := inferenceRenderMockImage(seed, request.Prompt, w, h)
		if err != nil {
			return InferenceImageGenerationResponse{}, util.HttpErr(http.StatusInternalServerError, "failed to render mock image")
		}

		encoded := base64.StdEncoding.EncodeToString(imageData)
		if request.ResponseFormat.GetOrDefault("") == "url" {
			response.Data = append(response.Data, InferenceImageGenerationResponseEl{
				URL: util.OptValue("data:image/png;base64," + encoded),
			})
		} else {
			response.Data = append(response.Data, InferenceImageGenerationResponseEl{
				B64JSON: util.OptValue(encoded),
			})
		}
	}

	if request.Quality.Present {
		response.Quality = request.Quality
	}
	if request.Size.Present {
		response.Size = request.Size
	}
	if request.OutputFormat.Present {
		response.OutputFormat = request.OutputFormat
	}
	if request.Background.Present {
		response.Background = request.Background
	}

	response.Usage = inferenceImageUsageFromPayload(request, len(response.Data), util.OptNone[InferenceImageGenerationUsage]())

	return response, nil
}

func inferenceRenderMockImage(seed string, prompt string, width int, height int) ([]byte, error) {
	hash := sha256.Sum256([]byte(seed))
	img := image.NewRGBA(image.Rect(0, 0, width, height))

	base := color.RGBA{R: hash[0], G: hash[1], B: hash[2], A: 255}
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			r := uint8((int(base.R) + x*37/width + y*19/height) % 256)
			g := uint8((int(base.G) + x*13/width + y*29/height) % 256)
			b := uint8((int(base.B) + x*23/width + y*11/height) % 256)
			img.SetRGBA(x, y, color.RGBA{R: r, G: g, B: b, A: 255})
		}
	}

	shapeCount := 5 + int(hash[3]%6)
	for i := 0; i < shapeCount; i++ {
		off := 4 + i*4
		c := color.RGBA{R: hash[off%32], G: hash[(off+1)%32], B: hash[(off+2)%32], A: 210}
		x := int(hash[(off+3)%32]) * width / 255
		y := int(hash[(off+5)%32]) * height / 255
		rw := max(20, int(hash[(off+7)%32])*width/510)
		rh := max(20, int(hash[(off+9)%32])*height/510)

		if i%2 == 0 {
			inferenceDrawRect(img, x-rw/2, y-rh/2, rw, rh, c)
		} else {
			inferenceDrawCircle(img, x, y, min(rw, rh)/2, c)
		}
	}

	scale := max(2, min(width/220, 6))
	lineHeight := 8 * scale
	maxLines := 3
	maxCharsPerLine := max(8, (width-24)/(6*scale))
	lines := inferenceWrapText(strings.ToUpper(prompt), maxCharsPerLine, maxLines)
	textBoxHeight := 12 + lineHeight*len(lines)
	inferenceDrawRect(img, 0, height-textBoxHeight, width, textBoxHeight, color.RGBA{R: 0, G: 0, B: 0, A: 220})

	y := height - textBoxHeight + 8
	for _, line := range lines {
		inferenceDrawBitmapText(img, 12, y, line, scale, color.RGBA{R: 245, G: 245, B: 245, A: 255})
		y += lineHeight
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		return nil, err
	}

	return buf.Bytes(), nil
}

func inferenceWrapText(text string, maxChars int, maxLines int) []string {
	if maxChars <= 0 || maxLines <= 0 {
		return []string{text}
	}

	words := strings.Fields(text)
	if len(words) == 0 {
		return []string{"(EMPTY PROMPT)"}
	}

	lines := make([]string, 0, maxLines)
	current := words[0]
	for _, w := range words[1:] {
		candidate := current + " " + w
		if len(candidate) <= maxChars {
			current = candidate
			continue
		}

		lines = append(lines, current)
		if len(lines) == maxLines {
			return lines
		}
		current = w
	}

	lines = append(lines, current)
	if len(lines) > maxLines {
		lines = lines[:maxLines]
	}

	if len(lines) == maxLines && len(words) > 1 {
		last := lines[maxLines-1]
		if len(last) > maxChars-3 {
			last = last[:max(0, maxChars-3)]
		}
		lines[maxLines-1] = last + "..."
	}

	return lines
}

func inferenceDrawRect(img *image.RGBA, x int, y int, w int, h int, c color.RGBA) {
	minX := max(0, x)
	minY := max(0, y)
	maxX := min(img.Bounds().Dx(), x+w)
	maxY := min(img.Bounds().Dy(), y+h)

	for yy := minY; yy < maxY; yy++ {
		for xx := minX; xx < maxX; xx++ {
			img.SetRGBA(xx, yy, c)
		}
	}
}

func inferenceDrawCircle(img *image.RGBA, cx int, cy int, radius int, c color.RGBA) {
	if radius <= 0 {
		return
	}

	for y := cy - radius; y <= cy+radius; y++ {
		if y < 0 || y >= img.Bounds().Dy() {
			continue
		}

		for x := cx - radius; x <= cx+radius; x++ {
			if x < 0 || x >= img.Bounds().Dx() {
				continue
			}

			dx := x - cx
			dy := y - cy
			if dx*dx+dy*dy <= radius*radius {
				img.SetRGBA(x, y, c)
			}
		}
	}
}

func inferenceDrawBitmapText(img *image.RGBA, x int, y int, text string, scale int, c color.RGBA) {
	px := x
	for _, r := range text {
		glyph := inferenceBitmapGlyph(r)
		for row := 0; row < len(glyph); row++ {
			for col := 0; col < 5; col++ {
				if glyph[row]&(1<<(4-col)) == 0 {
					continue
				}

				for sy := 0; sy < scale; sy++ {
					for sx := 0; sx < scale; sx++ {
						tx := px + col*scale + sx
						ty := y + row*scale + sy
						if tx >= 0 && tx < img.Bounds().Dx() && ty >= 0 && ty < img.Bounds().Dy() {
							img.SetRGBA(tx, ty, c)
						}
					}
				}
			}
		}

		px += 6 * scale
	}
}

func inferenceBitmapGlyph(r rune) [7]byte {
	switch r {
	case 'A':
		return [7]byte{0x0e, 0x11, 0x11, 0x1f, 0x11, 0x11, 0x11}
	case 'B':
		return [7]byte{0x1e, 0x11, 0x11, 0x1e, 0x11, 0x11, 0x1e}
	case 'C':
		return [7]byte{0x0e, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0e}
	case 'D':
		return [7]byte{0x1c, 0x12, 0x11, 0x11, 0x11, 0x12, 0x1c}
	case 'E':
		return [7]byte{0x1f, 0x10, 0x10, 0x1e, 0x10, 0x10, 0x1f}
	case 'F':
		return [7]byte{0x1f, 0x10, 0x10, 0x1e, 0x10, 0x10, 0x10}
	case 'G':
		return [7]byte{0x0f, 0x10, 0x10, 0x17, 0x11, 0x11, 0x0e}
	case 'H':
		return [7]byte{0x11, 0x11, 0x11, 0x1f, 0x11, 0x11, 0x11}
	case 'I':
		return [7]byte{0x1f, 0x04, 0x04, 0x04, 0x04, 0x04, 0x1f}
	case 'J':
		return [7]byte{0x1f, 0x02, 0x02, 0x02, 0x12, 0x12, 0x0c}
	case 'K':
		return [7]byte{0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11}
	case 'L':
		return [7]byte{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1f}
	case 'M':
		return [7]byte{0x11, 0x1b, 0x15, 0x15, 0x11, 0x11, 0x11}
	case 'N':
		return [7]byte{0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11}
	case 'O':
		return [7]byte{0x0e, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0e}
	case 'P':
		return [7]byte{0x1e, 0x11, 0x11, 0x1e, 0x10, 0x10, 0x10}
	case 'Q':
		return [7]byte{0x0e, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0d}
	case 'R':
		return [7]byte{0x1e, 0x11, 0x11, 0x1e, 0x14, 0x12, 0x11}
	case 'S':
		return [7]byte{0x0f, 0x10, 0x10, 0x0e, 0x01, 0x01, 0x1e}
	case 'T':
		return [7]byte{0x1f, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04}
	case 'U':
		return [7]byte{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0e}
	case 'V':
		return [7]byte{0x11, 0x11, 0x11, 0x11, 0x11, 0x0a, 0x04}
	case 'W':
		return [7]byte{0x11, 0x11, 0x11, 0x15, 0x15, 0x1b, 0x11}
	case 'X':
		return [7]byte{0x11, 0x11, 0x0a, 0x04, 0x0a, 0x11, 0x11}
	case 'Y':
		return [7]byte{0x11, 0x11, 0x11, 0x0a, 0x04, 0x04, 0x04}
	case 'Z':
		return [7]byte{0x1f, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1f}
	case '0':
		return [7]byte{0x0e, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0e}
	case '1':
		return [7]byte{0x04, 0x0c, 0x14, 0x04, 0x04, 0x04, 0x1f}
	case '2':
		return [7]byte{0x0e, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1f}
	case '3':
		return [7]byte{0x1f, 0x02, 0x04, 0x02, 0x01, 0x11, 0x0e}
	case '4':
		return [7]byte{0x02, 0x06, 0x0a, 0x12, 0x1f, 0x02, 0x02}
	case '5':
		return [7]byte{0x1f, 0x10, 0x1e, 0x01, 0x01, 0x11, 0x0e}
	case '6':
		return [7]byte{0x0e, 0x10, 0x1e, 0x11, 0x11, 0x11, 0x0e}
	case '7':
		return [7]byte{0x1f, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08}
	case '8':
		return [7]byte{0x0e, 0x11, 0x11, 0x0e, 0x11, 0x11, 0x0e}
	case '9':
		return [7]byte{0x0e, 0x11, 0x11, 0x0f, 0x01, 0x02, 0x0c}
	case ' ':
		return [7]byte{0, 0, 0, 0, 0, 0, 0}
	case '.', ',', ';', ':':
		return [7]byte{0, 0, 0, 0, 0, 0x04, 0x04}
	case '-', '_':
		return [7]byte{0, 0, 0, 0x1f, 0, 0, 0}
	case '!':
		return [7]byte{0x04, 0x04, 0x04, 0x04, 0x04, 0, 0x04}
	case '?':
		return [7]byte{0x0e, 0x11, 0x01, 0x02, 0x04, 0, 0x04}
	default:
		return [7]byte{0x1f, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1f}
	}
}
