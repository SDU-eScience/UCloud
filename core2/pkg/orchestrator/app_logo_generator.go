package orchestrator

import (
	"bytes"
	"image"
	"image/color"
	_ "image/jpeg"
	"image/png"
	"math"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"

	"golang.org/x/image/font/gofont/goregular"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"

	"golang.org/x/image/draw"
	"golang.org/x/image/font"
	"golang.org/x/image/font/opentype"
	"golang.org/x/image/math/fixed"
)

const (
	InvertColor          = 0xff40ff
	GrayscaleInvertColor = 0xff41ff
	DeleteColor          = 0xff42ff
	DarkenColor5         = 0xff43ff
	DarkenColor10        = 0xff44ff
	DarkenColor15        = 0xff45ff

	DarkBackground  = 0x282c2f
	LightBackground = 0xf8f8f9
)

var appLogo struct {
	cache sync.Map // map[string][]byte
	mu    sync.Mutex
	font  *opentype.Font
}

func initAppLogos() {
	appLogo.font = loadSystemFont()
}

const (
	logoTargetWidth        = 300
	logoMaxInputSize       = 1024 * 1024 * 20
	logoMaxInputDimensions = 10000
	logoMaxInputPixels     = 12_000_000
)

func ImageResize(data []byte, targetWidth int) []byte {
	if len(data) == 0 || len(data) > logoMaxInputSize {
		return nil
	}

	cfg, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return nil
	}

	if cfg.Width <= 0 || cfg.Height <= 0 {
		return nil
	}

	if cfg.Width > logoMaxInputDimensions || cfg.Height > logoMaxInputDimensions {
		return nil
	}

	if int64(cfg.Width)*int64(cfg.Height) > int64(logoMaxInputPixels) {
		return nil
	}

	dstW := targetWidth
	dstH := int(math.Round(float64(cfg.Height) * float64(dstW) / float64(cfg.Width)))
	if dstH <= 0 {
		return nil
	}

	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil
	}

	dst := image.NewRGBA(image.Rect(0, 0, dstW, dstH))
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, src.Bounds(), draw.Over, nil)

	var buf bytes.Buffer
	if err := png.Encode(&buf, dst); err != nil {
		return nil
	}
	return buf.Bytes()
}

func AppLogoValidateAndResize(data []byte) []byte {
	return ImageResize(data, logoTargetWidth)
}

func AppLogoInvalidate(id string) {
	appLogo.cache.Range(func(k, _ interface{}) bool {
		if strings.Contains(k.(string), id) {
			appLogo.cache.Delete(k)
		}
		return true
	})
}

func AppLogoGenerate(
	cacheKey, title string,
	input []byte,
	placeTextUnderLogo bool,
	backgroundColor int,
	colorReplacements map[int]int,
) []byte {
	// Output format is always PNG

	if v, ok := appLogo.cache.Load(cacheKey); ok {
		return v.([]byte)
	}

	img, _, err := image.Decode(bytes.NewReader(input))
	if len(input) != 0 && err != nil {
		return input
	}

	appLogo.mu.Lock()
	defer appLogo.mu.Unlock()

	if v, ok := appLogo.cache.Load(cacheKey); ok {
		return v.([]byte)
	}

	isEmpty := len(input) == 0

	rgba := toNRGBA(img)
	var w, h int
	if !isEmpty {
		w, h = rgba.Bounds().Dx(), rgba.Bounds().Dy()
	}
	resizeHeight := 256
	fontScale := 1.0
	if !isEmpty {
		fontScale = float64(h) / float64(resizeHeight)
	}

	hist := map[int]int{}
	firstX, firstY := w, h
	lastX, lastY := 0, 0

	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			i := y*w + x
			off := i * 4
			r, gCol, b, a := rgba.Pix[off], rgba.Pix[off+1], rgba.Pix[off+2], rgba.Pix[off+3]
			orig := (int(r) << 16) | (int(gCol) << 8) | int(b)

			for find, replace := range colorReplacements {
				if colorDistance(orig, find) < 80 {
					switch replace {
					case InvertColor:
						r, gCol, b = invert(r, gCol, b)
					case GrayscaleInvertColor:
						r, gCol, b = grayscaleInvert(r, gCol, b)
					case DarkenColor5:
						r, gCol, b = shade(r, gCol, b, 0.05)
					case DarkenColor10:
						r, gCol, b = shade(r, gCol, b, 0.10)
					case DarkenColor15:
						r, gCol, b = shade(r, gCol, b, 0.15)
					case DeleteColor:
						a = 0
					default:
						r = uint8((replace >> 16) & 0xff)
						gCol = uint8((replace >> 8) & 0xff)
						b = uint8(replace & 0xff)
					}
				}
			}

			rgba.Pix[off], rgba.Pix[off+1], rgba.Pix[off+2], rgba.Pix[off+3] = r, gCol, b, a
			if a >= 200 {
				argb := (int(a) << 24) | (int(r) << 16) | (int(gCol) << 8) | int(b)
				hist[argb]++
			}
			if argb := (int(a) << 24) | (int(r) << 16) | (int(gCol) << 8) | int(b); argb != 0xffffffff && a > 150 {
				if x < firstX {
					firstX = x
				}
				if y < firstY {
					firstY = y
				}
				if x > lastX {
					lastX = x
				}
				if y > lastY {
					lastY = y
				}
			}
		}
	}

	if firstX >= lastX || firstY >= lastY { // nothing but transparency/white
		firstX, firstY, lastX, lastY = 0, 0, 1, 1
	}

	crop := rgba
	if !isEmpty {
		// Clamp to image size, just in case something is broken
		firstX = min(max(0, firstX), w)
		firstY = min(max(0, firstY), h)
		lastX = min(max(0, lastX), w)
		lastY = min(max(0, lastY), h)

		crop = rgba.SubImage(image.Rect(firstX, firstY, lastX+1, lastY+1)).(*image.NRGBA)
		w, h = crop.Bounds().Dx(), crop.Bounds().Dy()
	} else {
		w, h = 0, 256
	}
	bestColor := pickBest(hist, backgroundColor)

	var out *image.NRGBA
	if placeTextUnderLogo && !isEmpty {
		out = composeVertical(crop, w, h, title, bestColor, fontScale)
	} else {
		out = composeHorizontal(crop, w, h, title, bestColor, fontScale, isEmpty)
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, out); err != nil {
		return input
	}
	res := buf.Bytes()
	appLogo.cache.Store(cacheKey, res)
	return res
}

// Helpers
// =====================================================================================================================

func composeVertical(img *image.NRGBA, w int, h int, title string, clr Rgb, scale float64) *image.NRGBA {
	size := 60.0 * scale
	textW, descent := textMetrics(title, size)
	if title == "" {
		size = 0
	}
	w = max(w, textW)
	h = h + int(size) + descent

	canvas := image.NewNRGBA(image.Rect(0, 0, w, h))
	if img != nil {
		dst := image.Rect(0, 0, img.Bounds().Dx(), img.Bounds().Dy())
		if img.Bounds().Dx() < w {
			diff := float64(w-img.Bounds().Dx()) / 2.0
			dst = dst.Add(image.Point{X: int(diff), Y: 0})
		}
		draw.Draw(canvas, dst, img, img.Bounds().Min, draw.Over)
	}
	drawString(canvas, title, (w-textW)/2, img.Bounds().Dy()+int(size*1.1), size, clr)
	return canvas
}

func composeHorizontal(img *image.NRGBA, imgW int, imgH int, title string, clr Rgb, scale float64, isEmpty bool) *image.NRGBA {
	paddingX := 0
	if !isEmpty {
		paddingX = int(30 * scale)
	}
	sizes := []float64{120, 110, 100, 90, 80, 70, 60, 50}
	for _, s := range sizes {
		size := s * scale
		lines, w, h := wrap(title, size)
		if title == "" {
			w, h, paddingX = 0, 0, 0
		}
		if len(lines) == 1 && h > int(float64(imgH)*0.55) {
			continue
		}
		if len(lines) > 1 && h > int(float64(imgH)*0.8) {
			continue
		}

		canvas := image.NewNRGBA(image.Rect(0, 0, imgW+paddingX+w, max(imgH, h)))
		if img != nil {
			dst := image.Rect(0, 0, img.Bounds().Dx(), img.Bounds().Dy())
			draw.Draw(canvas, dst, img, img.Bounds().Min, draw.Over)
		}

		y := (canvas.Bounds().Dy()-h)/2 + int(size)
		for _, line := range lines {
			drawString(canvas, line, imgW+paddingX, y, size, clr)
			y += int(size * 1.1)
		}
		return canvas
	}
	return img // fallback should never happen
}

func drawString(dst *image.NRGBA, txt string, x, y int, sz float64, clr Rgb) {
	if txt == "" {
		return
	}
	face, _ := opentype.NewFace(appLogo.font, &opentype.FaceOptions{Size: sz, DPI: 72})
	defer util.SilentClose(face)
	d := &font.Drawer{
		Dst:  dst,
		Src:  image.NewUniform(color.RGBA{uint8(clr.R), uint8(clr.G), uint8(clr.B), 255}),
		Face: face,
		Dot:  fixed.P(x, y),
	}
	d.DrawString(txt)
}

func textMetrics(txt string, sz float64) (width, descent int) {
	if txt == "" {
		return 0, 0
	}
	face, _ := opentype.NewFace(appLogo.font, &opentype.FaceOptions{Size: sz, DPI: 72})
	defer util.SilentClose(face)
	w := font.MeasureString(face, txt).Ceil()
	descent = face.Metrics().Descent.Ceil()
	return w, descent
}

func wrap(txt string, sz float64) (lines []string, width, height int) {
	words := strings.Fields(txt)
	if len(words) == 0 {
		return nil, 0, 0
	}
	lines = make([]string, 0, 2)
	if len(words) == 2 {
		lines = words
		for _, l := range lines {
			w, _ := textMetrics(l, sz)
			if w > width {
				width = w
			}
		}
	} else {
		var buf strings.Builder
		for _, w := range words {
			next := buf.String() + w + " "
			buf.WriteString(w + " ")
			if mw, _ := textMetrics(strings.TrimSpace(next), sz); mw > 250 {
				lines = append(lines, strings.TrimSpace(buf.String()))
				buf.Reset()
			}
		}
		if s := strings.TrimSpace(buf.String()); s != "" {
			lines = append(lines, s)
		}
		for _, l := range lines {
			if mw, _ := textMetrics(l, sz); mw > width {
				width = mw
			}
		}
	}
	ascent := int(sz)
	height = (len(lines)-1)*int(sz) + ascent
	return
}

// Basic image and color helpers
// =====================================================================================================================

func toNRGBA(src image.Image) *image.NRGBA {
	if src == nil {
		return nil
	}

	if dst, ok := src.(*image.NRGBA); ok {
		return dst
	}
	b := src.Bounds()
	dst := image.NewNRGBA(b)
	draw.Draw(dst, b, src, b.Min, draw.Src)
	return dst
}

func grayscaleInvert(r, g, b uint8) (uint8, uint8, uint8) {
	avg := uint8((int(r) + int(g) + int(b)) / 3)
	return 255 - avg, 255 - avg, 255 - avg
}

func invert(r, g, b uint8) (uint8, uint8, uint8) {
	return 255 - r, 255 - g, 255 - b
}

func shade(r, g, b uint8, pct float64) (uint8, uint8, uint8) {
	nr := int(float64(r) * (1 - pct))
	ng := int(float64(g) * (1 - pct))
	nb := int(float64(b) * (1 - pct))
	return uint8(nr), uint8(ng), uint8(nb)
}

func colorDistance(c1, c2 int) float64 {
	r1, g1, b1 := (c1>>16)&0xff, (c1>>8)&0xff, c1&0xff
	r2, g2, b2 := (c2>>16)&0xff, (c2>>8)&0xff, c2&0xff
	return math.Sqrt(float64((r2-r1)*(r2-r1) + (g2-g1)*(g2-g1) + (b2-b1)*(b2-b1)))
}

type Rgb struct{ R, G, B int }

func (c Rgb) contrast(o Rgb) float64 {
	l1, l2 := luminance(c), luminance(o)
	if l1 < l2 {
		l1, l2 = l2, l1
	}
	return (l1 + 0.05) / (l2 + 0.05)
}

func luminance(c Rgb) float64 {
	rc := comp(c.R)
	gc := comp(c.G)
	bc := comp(c.B)
	return rc*0.2126 + gc*0.7152 + bc*0.0722
}

func comp(v int) float64 {
	x := float64(v) / 255
	if x < 0.03928 {
		return x / 12.92
	}
	return math.Pow((x+0.055)/1.055, 2.4)
}

func pickBest(hist map[int]int, background int) Rgb {
	bg := Rgb{background >> 16 & 0xff, background >> 8 & 0xff, background & 0xff}
	best := Rgb{255 - bg.R, 255 - bg.G, 255 - bg.B}
	maxCount := -1
	for k, v := range hist {
		c := Rgb{k >> 16 & 0xff, k >> 8 & 0xff, k & 0xff}
		if c.contrast(bg) >= 2.2 && v > maxCount {
			best, maxCount = c, v
		}
	}
	if isGray(best) {
		if background == DarkBackground {
			return Rgb{255, 255, 255}
		}
		if background == LightBackground {
			return Rgb{0, 0, 0}
		}
	}
	return best
}

func isGray(c Rgb) bool {
	maxC := max(c.R, max(c.G, c.B))
	minC := min(c.R, min(c.G, c.B))
	return maxC-minC < 30
}

func loadSystemFont() *opentype.Font {
	var candidates []string

	switch runtime.GOOS {
	case "darwin":
		candidates = []string{
			"/System/Library/Fonts/SFNS.ttf",
			"/System/Library/Fonts/SFNSDisplay.ttf",
			"/System/Library/Fonts/Helvetica.ttc",
		}

	case "windows":
		windir := os.Getenv("WINDIR")
		candidates = []string{
			filepath.Join(windir, "Fonts", "segoeui.ttf"),
		}

	default:
		candidates = []string{
			"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
			"/usr/share/fonts/dejavu/DejaVuSans.ttf",
			"/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
		}
	}

	for _, p := range candidates {
		if data, err := os.ReadFile(p); err == nil {
			if f, err := opentype.Parse(data); err == nil {
				return f
			}
		}
	}

	// guaranteed fallback
	f, err := opentype.Parse(goregular.TTF)
	if err != nil {
		log.Fatal("fallback font must not fail parsing: %s", err)
	}
	return f
}
