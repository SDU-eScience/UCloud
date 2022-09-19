import * as React from "react";
import ReactMarkdown, {Options} from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";

function CodeBlock(props: {children: any; lang?: string; inline?: boolean}) {
    if (props.inline === true) return <code>{props.children}</code>;

    return (
        <SyntaxHighlighter language={props.lang}>
            {props.children}
        </SyntaxHighlighter>
    );
}

const LinkBlock: React.FunctionComponent<{href?: string}> = props => {
    return <ExternalLink color={"darkBlue"} href={props.href}>{props.children}</ExternalLink>;
};

const Markdown: React.FunctionComponent<Options> = props => {
    return <ReactMarkdown
        {...props}
        components={{
            a: LinkBlock,
            code: CodeBlock
        }}
    >
        {props.children}
    </ReactMarkdown>
};

export default Markdown;
