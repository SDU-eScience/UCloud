import {BoxProps} from "@/ui-components/Box";
import * as React from "react";
import {extractEventHandlers, injectStyleSimple, unbox} from "@/Unstyled";
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

export const FlexClass = injectStyleSimple("flex", ``);

const Flex: React.FunctionComponent<FlexCProps & { children?: React.ReactNode }> = props => {
    return <div
        className={FlexClass + " " + (props.className ?? "")}
        style={{
            display: "flex",
            flexDirection: props.flexDirection,
            flexWrap: props.flexWrap,
            gap: props.gap,
            ...unbox(props),
            ...(props.style ?? {})
        }}
        {...extractEventHandlers(props)}
        children={props.children}
    />
};

Flex.displayName = "Flex";

export default Flex;
