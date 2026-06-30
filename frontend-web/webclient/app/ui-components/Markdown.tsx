import * as React from "react";
import ReactMarkdown, {Options} from "react-markdown";
import ExternalLink from "./ExternalLink";
import SyntaxHighlighter from "react-syntax-highlighter";
import {injectStyle} from "@/Unstyled";
import {Icon, Text} from "@/ui-components/index";
import {UcxRenderContext} from "@/UCX/UcxView";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {doNothing} from "@/UtilityFunctions";
import {addStandardInputDialog} from "@/UtilityComponents";
import Table from "@/ui-components/Table";

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

export default Markdown;
