import * as React from "react";
import * as ReactMarkdown from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";

class CodeBlock extends React.PureComponent<{value: string; language?: string}> {
    public render(): JSX.Element {
        const {language, value} = this.props;

        return (
            <SyntaxHighlighter language={language}>
                {value}
            </SyntaxHighlighter>
        );
    }
}

const LinkBlock: React.FunctionComponent<{href: string}> = props => {
    return <ExternalLink color={"darkBlue"} href={props.href}>{props.children}</ExternalLink>;
};

const Markdown: React.FunctionComponent<ReactMarkdown.ReactMarkdownProps> = props => {
    return <ReactMarkdown {...props} renderers={{link: LinkBlock, code: CodeBlock}} />;
};

export default Markdown;
