import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";

const SectionClass = injectStyle("section", k => `
    ${k} {
        padding: 25px;
        border-radius: 25px;
    }
`);

export const Section: React.FunctionComponent<{highlight?: boolean; gap?: string; children?: React.ReactNode;}> = ({gap = "16px", ...props}) => {
    const style: CSSProperties = {};
    if (props.highlight === true) {
        style.background = "var(--borderColorHover)";
    } else {
        style.background = "var(--borderColor)";
    }
    if (gap !== undefined) {
        style.display = "grid";
        style.gap = gap;
    }

    return <section className={SectionClass} style={style} children={props.children} />;
}
