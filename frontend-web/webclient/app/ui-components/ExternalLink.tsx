import * as React from "react";
import BaseLink from "./BaseLink";
import {BaseLinkProps} from "./BaseLink";

type AAttr = React.AnchorHTMLAttributes<HTMLAnchorElement>;

const ExternalLink: React.FunctionComponent<
    BaseLinkProps & Pick<AAttr, Exclude<keyof AAttr, "rel" | "target">>
> = props => <BaseLink rel="noopener" target="_blank" {...props} />;

ExternalLink.defaultProps = {
    color: "text",
    hoverColor: "textHighlight"
};

export default ExternalLink;
