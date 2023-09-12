import {BoxProps} from "@/ui-components/Box";
import * as React from "react";
import {classConcat, extractDataTags, extractEventHandlers, injectStyleSimple, unbox} from "@/Unstyled";
import {CSSProperties} from "react";

export type FlexCProps =
    BoxProps &
    {
        gap?: string;
        flexDirection?: CSSProperties["flexDirection"];
        flexWrap?: CSSProperties["flexWrap"];
        className?: string;
        style?: CSSProperties;
    };

export const FlexClass = injectStyleSimple("flex", `
    display: flex;
`);

const Flex: React.FunctionComponent<React.PropsWithChildren<FlexCProps>> = props => {
    return <div
        className={classConcat(FlexClass, props.className)}
        style={{
            flexDirection: props.flexDirection,
            flexWrap: props.flexWrap,
            gap: props.gap,
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
