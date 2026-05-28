import * as React from "react";
import ReactMarkdown, {Options} from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";
import {injectStyle} from "@/Unstyled";

function CodeBlock(props: {lang?: string; inline?: boolean; children: React.ReactNode}) {
    if (props.inline === true || !props.lang) return <code>{props.children}</code>;

    return (
        <SyntaxHighlighter language={props.lang}>
            {props.children}
        </SyntaxHighlighter>
    );
}

function LinkBlock(props: {href?: string; children: React.ReactNode} & React.AnchorHTMLAttributes<HTMLAnchorElement>) {
    return <ExternalLink color={"primaryMain"} href={props.href}>{props.children}</ExternalLink>;
}

function Markdown(props: Options): React.ReactNode {
    return <ReactMarkdown
        {...props}
        components={{
            a: p => <LinkBlock href={p.href} children={p.children} />,
            code: p => <CodeBlock lang={p.lang} children={p.children} />
        }}
    />
}

export function SimpleMarkdown({children}: React.PropsWithChildren): React.ReactNode {
    return <ReactMarkdown
        components={{
            a: p => <LinkBlock href={p.href}>{p.children}</LinkBlock>
        }}
        allowedElements={["br", "a", "p", "strong", "b", "i", "em"]}
        children={children as string}
    />
}

const SingleLineClass = injectStyle("single-line", k => `
    ${k} {
        display: block;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        height: 1.5em;
    }

    ${k} p, ${k} br {
        display: inline;
        margin: 0;
    }
`);

export const SingleLineMarkdown: React.FunctionComponent<{children: string; width: string;}> = ({children, width}) => {
    return <div className={SingleLineClass} style={{width}}>
        <ReactMarkdown
            allowedElements={["br", "a", "p", "strong", "b", "i", "em"]}
            children={children}
        />
    </div>;
}

export default Markdown;
