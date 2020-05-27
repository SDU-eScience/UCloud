import * as React from "react";
import * as ReactMarkdown from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";
import {filePreviewPage, pathComponents, resolvePath} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {Link} from "ui-components/index";

class CodeBlock extends React.PureComponent<{ value: string; language?: string }> {
    public render(): JSX.Element {
        const { language, value } = this.props;

        return (
            <SyntaxHighlighter language={language}>
                {value}
            </SyntaxHighlighter>
        );
    }
}

const LinkBlock: React.FunctionComponent<{ href: string }> = props => {
    if (props.href.indexOf("http") !== 0) {
        // Consider this a file link
        const isRelative = props.href.indexOf("/") !== 0;

        let filePath: string;
        if (isRelative) {
            const path = decodeURIComponent(new URLSearchParams(location.search).get("path") ?? Client.homeFolder);
            let components = pathComponents(path);
            if (components.length > 0) {
                components.splice(components.length - 1, 1);
            }
            components = components.concat(props.href.split("/").filter(it => it !== ""));
            filePath = resolvePath(components.join("/"));
        } else {
            filePath = resolvePath(props.href);
        }

        return <Link color={"darkBlue"} to={filePreviewPage(filePath)}>{props.children}</Link>;
    }
    return <ExternalLink color={"darkBlue"} href={props.href}>{props.children}</ExternalLink>;
};

const Markdown: React.FunctionComponent<ReactMarkdown.ReactMarkdownProps> = props => {
    return <ReactMarkdown {...props} renderers={{ link: LinkBlock, code: CodeBlock }} />;
};

export default Markdown;
