import * as React from "react";
import {Link as ReactRouterLink, LinkProps as LProps} from "react-router-dom";
import {BaseLinkClass, BaseLinkProps} from "./BaseLink";
import {CSSProperties} from "react";
import {classConcat, extractDataTags, extractEventHandlers, unbox} from "@/Unstyled";

export type LinkProps = LProps & BaseLinkProps & {
    active?: boolean;
}

function Link({active, ...props}: LinkProps): JSX.Element {
    const style: CSSProperties = unbox(props);
    delete style["color"];
    if (props.hoverColor) style["--hoverColor"] = `var(--${props.hoverColor})`;
    if (props.color) style["--textColor"] = `var(--${props.color})`;

    return <ReactRouterLink
        className={classConcat(BaseLinkClass, props.className)}
        style={style}
        children={props.children}
        to={props.to}
        target={props.target}
        title={props.title}
        {...extractEventHandlers(props)}
        {...extractDataTags(props)}
    />
}

export default Link;
