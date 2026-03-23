package ucx

import "fmt"

var (
	SxDisplayBlock       = sxString("display", "block")
	SxDisplayInlineBlock = sxString("display", "inline-block")
	SxDisplayInlineFlex  = sxString("display", "inline-flex")
	SxDisplayFlex        = sxString("display", "flex")
	SxDisplayGrid        = sxString("display", "grid")
	SxDisplayInline      = sxString("display", "inline")
	SxDisplayNone        = sxString("display", "none")
)

var (
	SxAlignItemsStart    = sxString("alignItems", "flex-start")
	SxAlignItemsEnd      = sxString("alignItems", "flex-end")
	SxAlignItemsCenter   = sxString("alignItems", "center")
	SxAlignItemsBaseline = sxString("alignItems", "baseline")
	SxAlignItemsStretch  = sxString("alignItems", "stretch")
)

var (
	SxJustifyStart        = sxString("justifyContent", "flex-start")
	SxJustifyEnd          = sxString("justifyContent", "flex-end")
	SxJustifyCenter       = sxString("justifyContent", "center")
	SxJustifySpaceBetween = sxString("justifyContent", "space-between")
	SxJustifySpaceAround  = sxString("justifyContent", "space-around")
	SxJustifySpaceEvenly  = sxString("justifyContent", "space-evenly")
)

var (
	SxFlexWrapNoWrap = sxString("flexWrap", "nowrap")
	SxFlexWrapWrap   = sxString("flexWrap", "wrap")
)

var (
	SxBorderSolid  = sxString("borderStyle", "solid")
	SxBorderDashed = sxString("borderStyle", "dashed")
	SxBorderDotted = sxString("borderStyle", "dotted")
	SxBorderNone   = sxString("borderStyle", "none")
)

var (
	SxTextAlignLeft    = sxString("textAlign", "left")
	SxTextAlignCenter  = sxString("textAlign", "center")
	SxTextAlignRight   = sxString("textAlign", "right")
	SxTextAlignJustify = sxString("textAlign", "justify")
)

type SxOption func(out map[string]Value)

func Sx(options ...SxOption) Value {
	props := map[string]Value{}
	for _, option := range options {
		option(props)
	}
	return VObject(props)
}

func SxM(px int64) SxOption                   { return sxInt("m", px) }
func SxMx(px int64) SxOption                  { return sxInt("mx", px) }
func SxMy(px int64) SxOption                  { return sxInt("my", px) }
func SxMt(px int64) SxOption                  { return sxInt("mt", px) }
func SxMr(px int64) SxOption                  { return sxInt("mr", px) }
func SxMb(px int64) SxOption                  { return sxInt("mb", px) }
func SxMl(px int64) SxOption                  { return sxInt("ml", px) }
func SxP(px int64) SxOption                   { return sxInt("p", px) }
func SxPx(px int64) SxOption                  { return sxInt("px", px) }
func SxPy(px int64) SxOption                  { return sxInt("py", px) }
func SxPt(px int64) SxOption                  { return sxInt("pt", px) }
func SxPr(px int64) SxOption                  { return sxInt("pr", px) }
func SxPb(px int64) SxOption                  { return sxInt("pb", px) }
func SxPl(px int64) SxOption                  { return sxInt("pl", px) }
func SxGap(px int64) SxOption                 { return sxInt("gap", px) }
func SxWidth(px int64) SxOption               { return sxInt("width", px) }
func SxHeight(px int64) SxOption              { return sxInt("height", px) }
func SxMinHeight(px int64) SxOption           { return sxInt("minHeight", px) }
func SxMaxHeight(px int64) SxOption           { return sxInt("maxHeight", px) }
func SxMinWidth(px int64) SxOption            { return sxInt("minWidth", px) }
func SxMaxWidth(px int64) SxOption            { return sxInt("maxWidth", px) }
func SxBorderRadius(px int64) SxOption        { return sxInt("borderRadius", px) }
func SxBorderWidth(px int64) SxOption         { return sxInt("borderWidth", px) }
func SxBorderTopWidth(px int64) SxOption      { return sxInt("borderTopWidth", px) }
func SxBorderRightWidth(px int64) SxOption    { return sxInt("borderRightWidth", px) }
func SxBorderBottomWidth(px int64) SxOption   { return sxInt("borderBottomWidth", px) }
func SxBorderLeftWidth(px int64) SxOption     { return sxInt("borderLeftWidth", px) }
func SxFontSize(px int64) SxOption            { return sxInt("fontSize", px) }
func SxColor(color Color) SxOption            { return sxString("color", string(color)) }
func SxBg(color Color) SxOption               { return sxString("backgroundColor", string(color)) }
func SxBorderColor(color Color) SxOption      { return sxString("borderColor", string(color)) }
func SxBorderTopColor(color Color) SxOption   { return sxString("borderTopColor", string(color)) }
func SxBorderRightColor(color Color) SxOption { return sxString("borderRightColor", string(color)) }
func SxBorderBottomColor(color Color) SxOption {
	return sxString("borderBottomColor", string(color))
}
func SxBorderLeftColor(color Color) SxOption  { return sxString("borderLeftColor", string(color)) }
func SxFontWeight(weight string) SxOption     { return sxString("fontWeight", weight) }
func SxFontFamily(token string) SxOption      { return sxString("fontFamily", token) }
func SxLetterSpacing(px int64) SxOption       { return sxInt("letterSpacing", px) }
func SxTextTransform(value string) SxOption   { return sxString("textTransform", value) }
func SxLineHeight(lineHeight string) SxOption { return sxString("lineHeight", lineHeight) }
func SxWhiteSpace(value string) SxOption      { return sxString("whiteSpace", value) }
func SxWordBreak(value string) SxOption       { return sxString("wordBreak", value) }
func SxTextOverflow(value string) SxOption    { return sxString("textOverflow", value) }
func SxOverflow(value string) SxOption        { return sxString("overflow", value) }
func SxOverflowX(value string) SxOption       { return sxString("overflowX", value) }
func SxOverflowY(value string) SxOption       { return sxString("overflowY", value) }
func SxPosition(value string) SxOption        { return sxString("position", value) }
func SxGridTemplateColumns(value string) SxOption {
	return sxString("gridTemplateColumns", value)
}
func SxGridTemplateRows(value string) SxOption { return sxString("gridTemplateRows", value) }
func SxGridColumn(value string) SxOption       { return sxString("gridColumn", value) }
func SxGridRow(value string) SxOption          { return sxString("gridRow", value) }
func SxFlex(value string) SxOption             { return sxString("flex", value) }
func SxFlexGrow(value int64) SxOption          { return sxInt("flexGrow", value) }
func SxFlexShrink(value int64) SxOption        { return sxInt("flexShrink", value) }
func SxFlexBasis(value string) SxOption        { return sxString("flexBasis", value) }
func SxAlignSelf(value string) SxOption        { return sxString("alignSelf", value) }
func SxOpacity(value float64) SxOption         { return sxF64("opacity", value) }
func SxBoxShadow(value string) SxOption        { return sxString("boxShadow", value) }
func SxOutline(value string) SxOption          { return sxString("outline", value) }
func SxOutlineColor(color Color) SxOption      { return sxString("outlineColor", string(color)) }
func SxOutlineWidth(px int64) SxOption         { return sxInt("outlineWidth", px) }
func SxBackground(value string) SxOption       { return sxString("background", value) }
func SxBackgroundImage(value string) SxOption  { return sxString("backgroundImage", value) }
func SxBackgroundSize(value string) SxOption   { return sxString("backgroundSize", value) }
func SxBackgroundPosition(value string) SxOption {
	return sxString("backgroundPosition", value)
}
func SxTop(value string) SxOption    { return sxString("top", value) }
func SxRight(value string) SxOption  { return sxString("right", value) }
func SxBottom(value string) SxOption { return sxString("bottom", value) }
func SxLeft(value string) SxOption   { return sxString("left", value) }
func SxZIndex(value int64) SxOption  { return sxInt("zIndex", value) }
func SxWidthAuto() SxOption          { return sxString("width", "auto") }
func SxHeightAuto() SxOption         { return sxString("height", "auto") }
func SxWidthPercent(value int64) SxOption {
	return sxString("width", fmt.Sprintf("%d%%", value))
}
func SxHeightPercent(value int64) SxOption {
	return sxString("height", fmt.Sprintf("%d%%", value))
}

func sxInt(key string, value int64) SxOption {
	return func(out map[string]Value) {
		out[key] = VS64(value)
	}
}

func sxString(key string, value string) SxOption {
	return func(out map[string]Value) {
		out[key] = VString(value)
	}
}

func sxF64(key string, value float64) SxOption {
	return func(out map[string]Value) {
		out[key] = Value{Kind: ValueF64, F64: value}
	}
}
