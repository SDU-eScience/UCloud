import * as React from "react";
import { BoxProps } from "./Box";
import {extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";

export const LabelClass = injectStyle("label", k => `
    ${k} {
        width: 100%;
        color: var(--black);
        line-height: 2em;
        padding-left: 2px;
    }

    ${k} input {
        margin-left: -2px;
    }
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
