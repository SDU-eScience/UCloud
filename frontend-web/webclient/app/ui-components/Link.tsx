import * as React from "react";
import {Link as ReactRouterLink} from "react-router-dom";
import BaseLink from "./BaseLink";

const Link = ({active, ...props}: any) => <BaseLink as={ReactRouterLink} {...props} />;

export default Link;
