import * as React from "react";
import ReactMarkdown, {Options} from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";

function CodeBlock(props: {lang?: string; inline?: boolean; children: React.ReactNode & React.ReactNode[]}) {
    if (props.inline === true || !props.lang) return <code>{props.children}</code>;

    return (
        <SyntaxHighlighter language={props.lang}>
            {props.children}
        </SyntaxHighlighter>
    );
}

function LinkBlock(props: {href?: string; children: React.ReactNode & React.ReactNode[]}) {
    return <ExternalLink color={"darkBlue"} href={props.href}>{props.children}</ExternalLink>;
}

function Markdown(props: Options): React.ReactNode {

    return <ReactMarkdown
        {...props}
        components={{
            a: LinkBlock,
            code: CodeBlock
        }}
    />
}

export default Markdown;
