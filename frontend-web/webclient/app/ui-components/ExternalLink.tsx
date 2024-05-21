import * as React from "react";
import BaseLink from "./BaseLink";
import {BaseLinkProps} from "./BaseLink";

type AAttr = React.AnchorHTMLAttributes<HTMLAnchorElement>;

const ExternalLink: React.FunctionComponent<
    BaseLinkProps & Pick<AAttr, Exclude<keyof AAttr, "rel" | "target">>
> = ({color = "text", hoverColor = "primaryLight", ...props}) => <BaseLink rel="noopener" target="_blank" color={color} hoverColor={hoverColor} {...props} />;

export default ExternalLink;
