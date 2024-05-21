import Box from "./Box";
import * as React from "react";
import {CSSProperties} from "react";
import {extractSize} from "@/Unstyled";
import {BoxProps} from "./Types";

const Absolute: React.FunctionComponent<BoxProps & {
    top?: number | string;
    left?: number | string;
    bottom?: number | string;
    right?: number | string;
    style?: CSSProperties;
    children?: React.ReactNode;
}> = props => {
    const style: CSSProperties = {...(props.style ?? {}), position: "absolute"};
    if (props.top !== undefined) style.top = extractSize(props.top);
    if (props.left !== undefined) style.left = extractSize(props.left);
    if (props.right !== undefined) style.right = extractSize(props.right);
    if (props.bottom !== undefined) style.bottom = extractSize(props.bottom);

    return <Box
        style={style}
        children={props.children}
        {...props}
    />;
};

Absolute.displayName = "Absolute";

export default Absolute;
