import * as React from "react";
import {Link as ReactRouterLink, LinkProps} from "react-router-dom";
import {BaseLinkClass, BaseLinkProps} from "./BaseLink";
import {CSSProperties} from "react";
import {extractEventHandlers, unbox} from "@/Unstyled";

const Link = ({active, ...props}: LinkProps & BaseLinkProps & {active?: boolean}): JSX.Element => {
    const style: CSSProperties = unbox(props);
    if (props.hoverColor) style["--hoverColor"] = `var(--${props.hoverColor})`;

    return <ReactRouterLink
        className={BaseLinkClass}
        style={style}
        children={props.children}
        to={props.to}
        {...extractEventHandlers(props)}
    />
}

export default Link;
