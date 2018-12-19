import * as React from "react";
import * as ReactMarkdown from "react-markdown";
import ExternalLink from "./ExternalLink";

const LinkBlock: React.FunctionComponent<{ href: string }> = props => {
    return <ExternalLink color={"darkBlue"} href={props.href}>{props.children}</ExternalLink>;
};

const Markdown: React.FunctionComponent<ReactMarkdown.ReactMarkdownProps> = props => {
    return <ReactMarkdown {...props} renderers={{ link: LinkBlock }} />;
};

export default Markdown;