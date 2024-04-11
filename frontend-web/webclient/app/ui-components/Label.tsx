import * as React from "react";
import {classConcat, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {BoxProps} from "./Types";

export const LabelClass = injectStyle("label", k => `
    ${k} {
        width: 100%;
        color: var(--textPrimary);
    }
`);

function Label(props: BoxProps & {className?: string; children?: React.ReactNode; style?: CSSProperties & Record<`--${string}`, string>; htmlFor?: string}) {
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
