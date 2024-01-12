import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";

const SectionClass = injectStyle("section", k => `
    ${k} {
        padding: 25px;
        border-radius: 25px;
    }
`);

export const Section: React.FunctionComponent<{ highlight?: boolean; gap?: string; children?: React.ReactNode; }> = props => {
    const style: CSSProperties = {};
    if (props.highlight === true) {
        style.background = "var(--borderColorHover)";
    } else {
        style.background = "var(--borderColor)";
    }
    if (props.gap !== undefined) {
        style.display = "grid";
        style.gap = props.gap;
    }

    return <section className={SectionClass} style={style} children={props.children} />;
}

Section.defaultProps = {
    gap: "16px"
};
