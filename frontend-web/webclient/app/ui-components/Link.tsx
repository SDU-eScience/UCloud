import * as React from "react";
import {Link as ReactRouterLink, LinkProps} from "react-router-dom";
import BaseLink, {BaseLinkProps} from "./BaseLink";

const Link = ({active, ...props}: LinkProps & BaseLinkProps & {active?: boolean}): JSX.Element =>
    <BaseLink as={ReactRouterLink} {...props} />;

export default Link;
