import {injectStyleSimple, unbox} from "@/Unstyled";
import * as React from "react";
import {BoxProps} from "./Types";

export const DividerClass = injectStyleSimple("divider", `
    border: 0;
    border-bottom-style: solid;
    border-bottom-width: 2px;
    border-color: var(--borderColor);
    margin-left: 0;
    margin-right: 0;
`);

const Divider: React.FunctionComponent<BoxProps & {borderColor?: string}> = props => {
    return <hr className={DividerClass} style={{borderColor: props.borderColor, ...unbox(props)}} />
};

Divider.displayName = "Divider";

export default Divider;
