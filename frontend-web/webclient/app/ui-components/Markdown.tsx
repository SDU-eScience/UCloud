import * as React from "react";
import ReactMarkdown, {Options} from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";
import {injectStyle} from "@/Unstyled";
import Table from "@/ui-components/Table";
import Box from "@/ui-components/Box";
import CodeSnippet from "@/ui-components/CodeSnippet";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css"


function CodeBlock(props: {lang?: string; inline?: boolean; children: React.ReactNode}) {
    if (props.inline === true || !props.lang) return <code>{props.children}</code>;

    return (
        <SyntaxHighlighter language={props.lang}>
            {props.children as string}
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

export function MarkdownTable({children}: React.PropsWithChildren): React.ReactNode {
    const wrapperRef = React.useRef<HTMLDivElement | null>(null);
    const [layout, setLayout] = React.useState({scroll: false, minWidth: 0});

    React.useLayoutEffect(() => {
        const wrapper = wrapperRef.current;
        if (!wrapper) return;

        let frame = 0;
        const measure = () => {
            window.cancelAnimationFrame(frame);
            frame = window.requestAnimationFrame(() => {
                const next = measureMarkdownTable(wrapper);
                setLayout(prev => prev.scroll === next.scroll && prev.minWidth === next.minWidth ? prev : next);
            });
        };

        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(wrapper);
        return () => {
            window.cancelAnimationFrame(frame);
            observer.disconnect();
        };
    }, [children]);

    return <div ref={wrapperRef} style={{overflowX: layout.scroll ? "auto" : "visible", maxWidth: "100%"}}>
        <Table tableType="presentation" minWidth={layout.scroll ? `${layout.minWidth}px` : undefined}>
            {children}
        </Table>
    </div>;
}

export function DocumentTypography({className, ...props}: React.HTMLAttributes<HTMLDivElement>): React.ReactNode {
    const classes = className ? `${DocumentTypographyClass} ${className}` : DocumentTypographyClass;
    return <div {...props} className={classes} />;
}

function measureMarkdownTable(wrapper: HTMLDivElement): { scroll: boolean; minWidth: number } {
    const rows = Array.from(wrapper.querySelectorAll("tr"));
    const availableWidth = wrapper.clientWidth;
    if (rows.length === 0 || availableWidth === 0) {
        return {scroll: false, minWidth: 0};
    }

    const columnStats: Array<{ textLength: number; tokenLength: number; renderedWidth: number }> = [];
    for (const row of rows) {
        const cells = Array.from(row.children).filter((cell): cell is HTMLElement => cell instanceof HTMLElement);
        for (let idx = 0; idx < cells.length; idx++) {
            const cell = cells[idx];
            const text = (cell.textContent ?? "").replace(/\s+/g, " ").trim();
            const longestToken = text.split(/\s+/).reduce((longest, token) => Math.max(longest, token.length), 0);
            const stats = columnStats[idx] ?? {textLength: 0, tokenLength: 0, renderedWidth: 0};
            stats.textLength = Math.max(stats.textLength, text.length);
            stats.tokenLength = Math.max(stats.tokenLength, longestToken);
            stats.renderedWidth = Math.max(stats.renderedWidth, cell.getBoundingClientRect().width);
            columnStats[idx] = stats;
        }
    }

    let forceScroll = false;
    const desiredWidth = columnStats.reduce((sum, stats) => {
        let columnWidth = 120;
        if (stats.tokenLength >= 40) {
            forceScroll = true;
            columnWidth = clamp(stats.tokenLength * 8, 240, 720);
        } else if (stats.textLength >= 120) {
            columnWidth = clamp(stats.textLength * 5, 320, 640);
        } else if (stats.textLength >= 60) {
            columnWidth = clamp(stats.textLength * 4, 200, 360);
        } else {
            columnWidth = clamp(stats.textLength * 6 + 24, 80, 200);
        }
        return sum + Math.max(columnWidth, Math.min(stats.renderedWidth, 240));
    }, 0);

    const minWidth = Math.ceil(Math.max(desiredWidth, availableWidth));
    const shouldScroll = forceScroll || desiredWidth > availableWidth + 8;
    return {scroll: shouldScroll, minWidth: shouldScroll ? minWidth : 0};
}

function clamp(value: number, min: number, max: number): number {
    return Math.max(min, Math.min(max, value));
}

export function MarkdownDocument({text}: { text: string }): React.ReactNode {
    if (text.trim() === "") return null;
    const normalizedText = normalizeMath(text);
    return (
        <DocumentTypography>
            <ReactMarkdown
                components={{
                    a: (p) => <ExternalLink href={p.href}>{p.children}</ExternalLink>,
                    pre: (p) => <Box my={16}><CodeSnippet children={p.children} maxHeight=""/></Box>,
                    table: p => <MarkdownTable>{p.children}</MarkdownTable>,
                }}
                allowedElements={[
                    "h1",
                    "h2",
                    "h3",
                    "h4",
                    "h5",
                    "h6",
                    "br",
                    "a",
                    "p",
                    "strong",
                    "b",
                    "i",
                    "em",
                    "ul",
                    "ol",
                    "li",
                    "pre",
                    "code",
                    "table",
                    "th",
                    "tbody",
                    "thead",
                    "td",
                    "tr",
                    "hr",
                    "blockquote",

                    // katex + mathml
                    'span',
                    'math', 'semantics', 'mrow', 'mi', 'mo', 'mn', 'msup', 'msub',
                    'msubsup', 'mfrac', 'msqrt', 'mroot', 'mtable', 'mtr', 'mtd',
                    'mtext', 'annotation',
                ]}
                children={normalizedText}
                remarkPlugins={[remarkGfm, remarkMath]}
                rehypePlugins={[rehypeKatex]}
            />
        </DocumentTypography>
    );
}

function normalizeMath(markdown: string): string {
    return markdown
        .replace(/\\\[((?:.|\n)*?)\\\]/g, (_, m) => `$$\n${m}\n$$`)
        .replace(/\\\(((?:\\.|[^\\)])*?)\\\)/g, (_, m) => `$${m}$`);
}

const DocumentTypographyClass = injectStyle("document-typography", k => `
    ${k} {
        color: var(--textPrimary);
        font-size: 15px;
        line-height: 1.65;
    }

    ${k} h1, ${k} h2, ${k} h3, ${k} h4, ${k} h5, ${k} h6 {
        padding-bottom: 5px;
        margin: 24px 0 16px;
        line-height: 1.3;
        letter-spacing: -0.01em;
    }

    ${k} h1 { font-size: 23px; }
    ${k} h2 { font-size: 21px; }
    ${k} h3 { font-size: 19px; }
    ${k} h4 { font-size: 17px; }
    ${k} h5 { font-size: 15px; }
    ${k} h6 { font-size: 13px; }

    ${k} p + h1, ${k} p + h2, ${k} p + h3, ${k} p + h4, ${k} p + h5, ${k} p + h6,
    ${k} h1:first-child, ${k} h2:first-child, ${k} h3:first-child,
    ${k} h4:first-child, ${k} h5:first-child, ${k} h6:first-child {
        margin-top: 0;
    }

    ${k} h1, ${k} h2 {
        border-bottom: 1px solid var(--document-border, var(--borderColor));
    }

    ${k} p {
        margin-top: 0;
        margin-bottom: 16px;
    }

    ${k} p:last-child {
        margin-bottom: 0;
    }

    ${k} code {
        white-space: break-spaces;
        background: var(--playground-active);
        border-radius: 6px;
        padding: .2em .4em;
        font-size: 85%;
    }

    ${k} hr {
        display: block;
        margin: 24px 0;
        border: none;
        height: .25em;
        background: var(--document-border, var(--borderColor));
        width: 100%;
    }

    ${k} blockquote {
        color: var(--textSecondary);
        border-left: .25em solid var(--document-border, var(--borderColor));
        padding: 0 1em;
        margin: 0;
    }

    ${k} ul, ${k} ol {
        padding-left: 2em;
        margin-top: 0;
        margin-bottom: 16px;
    }

    ${k} strong {
        font-weight: bold;
    }
`);


export default Markdown;
