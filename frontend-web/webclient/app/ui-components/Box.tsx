import {
    AlignItemsProps, ColorProps, HeightProps, MaxHeightProps, MaxWidthProps, MinHeightProps, MinWidthProps,
    OverflowProps, TextAlignProps, WidthProps, ZIndexProps, BackgroundProps, JustifyContentProps,
    BorderRadiusProps, VerticalAlignProps, FontSizeProps, SpaceProps
} from "styled-system";
import * as React from "react";

import {Cursor} from "./Types";
import {classConcat, extractEventHandlers, injectStyleSimple, unbox, unboxDataTags, WithEventHandlers} from "@/Unstyled";


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
    style?: React.CSSProperties;
    className?: string;
}> = props => {
    return <div
        className={classConcat(BoxClass, props.className)}
        style={{...unbox(props), ...(props.style ?? {})}}
        {...unboxDataTags(props as Record<string, string>)}
        title={props.title}
        ref={props.divRef}
        children={props.children}
        {...extractEventHandlers(props)}
    />;
}

Box.displayName = "Box";

export default Box;
