package ucx

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
func SxMt(px int64) SxOption                  { return sxInt("mt", px) }
func SxMr(px int64) SxOption                  { return sxInt("mr", px) }
func SxMb(px int64) SxOption                  { return sxInt("mb", px) }
func SxMl(px int64) SxOption                  { return sxInt("ml", px) }
func SxP(px int64) SxOption                   { return sxInt("p", px) }
func SxPt(px int64) SxOption                  { return sxInt("pt", px) }
func SxPr(px int64) SxOption                  { return sxInt("pr", px) }
func SxPb(px int64) SxOption                  { return sxInt("pb", px) }
func SxPl(px int64) SxOption                  { return sxInt("pl", px) }
func SxGap(px int64) SxOption                 { return sxInt("gap", px) }
func SxWidth(px int64) SxOption               { return sxInt("width", px) }
func SxHeight(px int64) SxOption              { return sxInt("height", px) }
func SxMinWidth(px int64) SxOption            { return sxInt("minWidth", px) }
func SxMaxWidth(px int64) SxOption            { return sxInt("maxWidth", px) }
func SxBorderRadius(px int64) SxOption        { return sxInt("borderRadius", px) }
func SxBorderWidth(px int64) SxOption         { return sxInt("borderWidth", px) }
func SxFontSize(px int64) SxOption            { return sxInt("fontSize", px) }
func SxColor(color Color) SxOption            { return sxString("color", string(color)) }
func SxBg(color Color) SxOption               { return sxString("backgroundColor", string(color)) }
func SxBorderColor(color Color) SxOption      { return sxString("borderColor", string(color)) }
func SxFontWeight(weight string) SxOption     { return sxString("fontWeight", weight) }
func SxLineHeight(lineHeight string) SxOption { return sxString("lineHeight", lineHeight) }

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
