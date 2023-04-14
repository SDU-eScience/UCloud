import {injectStyleSimple, unbox} from "@/Unstyled";
import {BoxProps} from "@/ui-components/Box";
import * as React from "react";

export const DividerClass = injectStyleSimple("divider", `
    border: 0;
    border-bottom-style: solid;
    border-bottom-width: 2px;
    border-color: var(--blue);
    margin-left: 0;
    margin-right: 0;
`);

const Divider: React.FunctionComponent<BoxProps & {borderColor?: string}> = props => {
    return <hr className={DividerClass} style={{borderColor: props.borderColor, ...unbox(props)}} />
};

Divider.displayName = "Divider";

export default Divider;
