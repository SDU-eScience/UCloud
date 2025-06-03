package accounting

import (
	"bytes"
	"errors"
	"fmt"
	"image"
	_ "image/gif"  // register decoder
	_ "image/jpeg" // register decoder
	"image/png"
	"io"
	"math"
)

// hard upper-bound to protect against decompression bombs
const maxDecodePixels = 20 * 1000 * 1000 // 20 mega-pixels

// rescaleLogo will accept unknown user data assumed to contain an image it will return a rescaled image according to
// the dimensions in maxWidth and maxHeight while preserving aspect ratio.
//
// If the image is smaller than maxWidth/maxHeight implies then the image is not rescaled. The returned image will be
// PNG encoded. If the data does not contain an image of a known format, an error is returned.
func rescaleLogo(data []byte, maxWidth, maxHeight int) ([]byte, error) {
	if maxWidth <= 0 || maxHeight <= 0 {
		return nil, errors.New("maxWidth and maxHeight must be positive")
	}

	// Read the header first to learn dimensions and format. Reject the image immediately if it is too big.
	r := bytes.NewReader(data)
	cfg, _, err := image.DecodeConfig(r)
	if err != nil {
		return nil, fmt.Errorf("image decode config: %w", err)
	}
	if cfg.Width <= 0 || cfg.Height <= 0 {
		return nil, errors.New("invalid image dimensions")
	}
	if px := cfg.Width * cfg.Height; px > maxDecodePixels {
		return nil, fmt.Errorf("image too large: %d pixels (limit %d)", px, maxDecodePixels)
	}

	// Reset reader and do the full decode now that we know it’s safe enough.
	if _, err = r.Seek(0, io.SeekStart); err != nil {
		return nil, fmt.Errorf("rewind reader: %w", err)
	}
	src, _, err := image.Decode(r)
	if err != nil {
		return nil, fmt.Errorf("decode image: %w", err)
	}

	// Determine target size while preserving aspect ratio.
	newW, newH := cfg.Width, cfg.Height
	if cfg.Width > maxWidth || cfg.Height > maxHeight {
		rx := float64(maxWidth) / float64(cfg.Width)
		ry := float64(maxHeight) / float64(cfg.Height)
		scale := math.Min(rx, ry)
		newW = int(math.Round(float64(cfg.Width) * scale))
		newH = int(math.Round(float64(cfg.Height) * scale))
	}

	// If no resize needed we still convert to PNG for consistency.
	var dst image.Image
	if newW == cfg.Width && newH == cfg.Height {
		dst = src
	} else {
		// Simple nearest-neighbour scaler
		//
		// NOTE(Dan): If this turns out to be a performance bottleneck/quality problem, then consider adding
		// golang.org/x/image as a dependency.

		rgba := image.NewRGBA(image.Rect(0, 0, newW, newH))
		sb := src.Bounds()
		sw, sh := sb.Dx(), sb.Dy()
		for y := 0; y < newH; y++ {
			sy := sb.Min.Y + (y*sh+newH/2)/newH
			for x := 0; x < newW; x++ {
				sx := sb.Min.X + (x*sw+newW/2)/newW
				rgba.Set(x, y, src.At(sx, sy))
			}
		}
		dst = rgba
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, dst); err != nil {
		return nil, fmt.Errorf("encode PNG: %w", err)
	}
	return buf.Bytes(), nil
}
