import * as React from 'react';
import { Link as ReactRouterLink } from "react-router-dom";
import BaseLink from "./BaseLink";

const Link = (props) => <BaseLink as={ReactRouterLink} {...props} />;


export default Link;
