import * as React from "react";
import { BoxProps } from "./Box";
import {extractEventHandlers, injectStyleSimple, unbox} from "@/Unstyled";
import {CSSProperties} from "react";

export const LabelClass = injectStyleSimple("label", `
    width: 100%;
    font-weight: bold;
    color: var(--black);
`);

const Label: React.FunctionComponent<BoxProps & { children?: React.ReactNode; style?: CSSProperties; htmlFor?: string; }> = props => {
    return <label
        className={LabelClass}
        htmlFor={props.htmlFor}
        style={{...unbox(props), ...(props.style ?? {})}}
        children={props.children}
        {...extractEventHandlers(props)}
    />
};

Label.displayName = "Label";

export default Label;
