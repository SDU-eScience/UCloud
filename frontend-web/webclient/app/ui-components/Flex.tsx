import {BoxProps} from "@/ui-components/Box";
import * as React from "react";
import {classConcat, extractDataTags, extractEventHandlers, injectStyleSimple, unbox} from "@/Unstyled";
import {CSSProperties} from "react";

export type FlexCProps =
    BoxProps &
    {
        gap?: string;
        columnGap?: string;
        rowGap?: string;
        flexDirection?: CSSProperties["flexDirection"];
        flexWrap?: CSSProperties["flexWrap"];
        className?: string;
        style?: CSSProperties;
    };

export const FlexClass = injectStyleSimple("flex", `
    display: flex;
`);

const Flex: React.FunctionComponent<React.PropsWithChildren<FlexCProps>> = props => {
    const style: CSSProperties = {};
    if (props.flexDirection) style.flexDirection = props.flexDirection;
    if (props.flexWrap) style.flexWrap = props.flexWrap;
    if (props.gap) style.gap = props.gap;
    if (props.columnGap) style.columnGap = props.columnGap;
    if (props.rowGap) style.rowGap = props.rowGap;

    return <div
        className={classConcat(FlexClass, props.className)}
        style={{
            ...style,
            ...unbox(props),
            ...(props.style ?? {})
        }}
        {...extractEventHandlers(props)}
        {...extractDataTags(props as Record<string, string>)}
        children={props.children}
    />
};

Flex.displayName = "Flex";

export default Flex;
