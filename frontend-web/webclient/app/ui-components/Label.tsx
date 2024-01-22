import * as React from "react";
import {BoxProps} from "./Box";
import {classConcat, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";

export const LabelClass = injectStyle("label", k => `
    ${k} {
        width: 100%;
        color: var(--textPrimary);
        padding-left: 2px;
    }

    ${k} input {
        margin-left: -2px;
    }
`);

const Label: React.FunctionComponent<BoxProps & {className?: string; children?: React.ReactNode; style?: CSSProperties; htmlFor?: string;}> = props => {
    return <label
        className={classConcat(LabelClass, props.className)}
        htmlFor={props.htmlFor}
        style={{...unbox(props), ...(props.style ?? {})}}
        children={props.children}
        {...extractEventHandlers(props)}
    />
};

Label.displayName = "Label";

export default Label;
