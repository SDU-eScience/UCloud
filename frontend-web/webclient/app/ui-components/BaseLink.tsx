import * as React from "react";
import {classConcat, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {ThemeColor} from "./theme";
import {BoxProps} from "./Types";

export interface BaseLinkProps extends BoxProps {
    hoverColor?: ThemeColor;
    className?: string;
    href?: string;
    target?: string;
    children?: React.ReactNode;
    rel?: string;
    title?: string;
}

export const BaseLinkClass = injectStyle("base-link", k => `
    ${k} {
        --hoverColor: var(--linkColorHover);
        --textColor: var(--linkColor);
        cursor: pointer;
        text-decoration: none;
        color: var(--textColor);
    }
    
    ${k}:hover {
        color: var(--hoverColor);
    }
`);

const BaseLink: React.FunctionComponent<BaseLinkProps> = props => {
    const style: CSSProperties = unbox(props);
    delete style.color; // Overrides color in hover if present.
    if (props.hoverColor) style["--hoverColor"] = `var(--${props.hoverColor})`;
    if (props.color && typeof props.color === "string") {
        style["--textColor"] = `var(--${props.color})`;
    }

    return <a
        className={classConcat(BaseLinkClass, props.className)}
        style={style}
        children={props.children}
        href={props.href}
        target={props.target}
        rel={props.rel}
        title={props.title}
        {...extractEventHandlers(props)}
    />
};

BaseLink.displayName = "BaseLink";

export default BaseLink;
