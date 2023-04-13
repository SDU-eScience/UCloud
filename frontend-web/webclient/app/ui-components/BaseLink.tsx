import {BoxProps} from "@/ui-components/Box";
import * as React from "react";
import {extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {ThemeColor} from "./theme";

export interface BaseLinkProps extends BoxProps {
    hoverColor?: ThemeColor;
    href?: string;
    target?: string;
    children?: React.ReactNode;
    rel?: string;
}

export const BaseLinkClass = injectStyle("base-link", k => `
    ${k} {
        cursor: pointer;
        text-decoration: none;
        color: var(--text);
        
        --hoverColor: var(--textHighlight);
    }
    
    ${k}:hover {
        color: var(--hoverColor);
    }
`);

const BaseLink: React.FunctionComponent<BaseLinkProps> = props => {
    const style: CSSProperties = unbox(props);
    if (props.hoverColor) style["--hoverColor"] = `var(--${props.hoverColor})`;

    return <a
        className={BaseLinkClass}
        style={style}
        children={props.children}
        href={props.href}
        target={props.target}
        rel={props.rel}
        {...extractEventHandlers(props)}
    />
};

BaseLink.displayName = "BaseLink";

export default BaseLink;
