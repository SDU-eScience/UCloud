import {WithEventHandlers} from "@/Unstyled";
import {
    AlignItemsProps, ColorProps, HeightProps, MaxHeightProps, MaxWidthProps, MinHeightProps, MinWidthProps,
    OverflowProps, TextAlignProps, WidthProps, ZIndexProps, BackgroundProps, JustifyContentProps,
    BorderRadiusProps, VerticalAlignProps, FontSizeProps, SpaceProps
} from "styled-system";

export type Cursor = "auto" | "default" | "none" | "context-menu" | "help" | "pointer" | "progress" | "wait" | "cell" |
    "crosshair" | "text" | "vertical-text" | "alias" | "copy" | "move" | "no-drop" | "not-allowed" | "e-resize" |
    "n-resize" | "ne-resize" | "nw-resize" | "s-resize" | "se-resize" | "sw-resize" | "w-resize" | "ew-resize" |
    "ns-resize" | "nesw-resize" | "nwse-resize" | "col-resize" | "row-resize" | "all-scroll" | "zoom-in" | "zoom-out" |
    "grab" | "grabbing" | "inherit";


interface FlexGrowProps {
    flexGrow?: number;
    flexShrink?: number;
    flexBasis?: number;
}

interface FlexShrinkProps {
    flexShrink?: number;
}

export type BoxProps =
    SpaceProps &
    WidthProps &
    MinWidthProps &
    ColorProps &
    BackgroundProps &
    AlignItemsProps &
    JustifyContentProps &
    HeightProps &
    MinHeightProps &
    MaxHeightProps &
    MaxWidthProps &
    FlexGrowProps &
    FlexShrinkProps &
    ZIndexProps &
    TextAlignProps &
    OverflowProps &
    BorderRadiusProps &
    VerticalAlignProps &
    FontSizeProps &
    {cursor?: Cursor} &
    {objectFit?: "contain" | "cover" | "fill" | "none" | "scale-down"} &
    WithEventHandlers;
