import {
    AlignItemsProps, ColorProps, HeightProps, MaxHeightProps, MaxWidthProps, MinHeightProps, MinWidthProps,
    OverflowProps, SpaceProps, TextAlignProps, WidthProps, ZIndexProps, BackgroundProps, JustifyContentProps,
    BorderRadiusProps, VerticalAlignProps, FontSizeProps
} from "styled-system";
import {Cursor} from "./Types";
import {extractEventHandlers, injectStyleSimple, unbox, WithEventHandlers} from "@/Unstyled";
import * as React from "react";
import {CSSProperties} from "react";

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
    { cursor?: Cursor } &
    WithEventHandlers;

interface FlexGrowProps {
    flexGrow?: number;
}

interface FlexShrinkProps {
    flexShrink?: number;
}

export const BoxClass = injectStyleSimple("box", ``);
const Box: React.FunctionComponent<BoxProps & {
    children?: React.ReactNode;
    divRef?: React.RefObject<HTMLDivElement>;
    title?: string;
    style?: CSSProperties;
    className?: string;
}> = props => {
    return <div
        className={BoxClass + " " + (props.className ?? "")}
        style={{...unbox(props), ...(props.style ?? {})}}
        title={props.title}
        ref={props.divRef}
        children={props.children}
        {...extractEventHandlers(props)}
    />;
}

Box.displayName = "Box";

export default Box;
