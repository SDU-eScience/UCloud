import * as React from "react";
import {injectStyle} from "@/Unstyled";
import Icon from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {useCallback} from "react";
import {copyToClipboard} from "@/UtilityFunctions";
import {sendSuccessNotification} from "@/Notifications";
import SyntaxHighlighter from "react-syntax-highlighter";
import { atomOneDark } from 'react-syntax-highlighter/dist/esm/styles/hljs';

const Style = injectStyle("code-snippet", k => `
    ${k} {
        position: relative;
    }

    ${k} button {
        background: rgba(255, 255, 255, 30%);
        border-radius: 8px;
        padding: 8px;
        position: absolute;
        top: 8px;
        right: 16px;
        border: 0;
        outline: none;
        z-index: 1;
    }
`);

const CodeSnippet: React.FunctionComponent<{
    children: React.ReactNode,
    maxHeight: string,
    lang?: string,
    inline?: boolean,
}> = ({children, maxHeight, lang, inline}) => {
    const code = codeSnippetText(children);
    const l = lang ?? codeSnippetLanguage(children) ?? "text";
    const doCopy = useCallback((e: React.MouseEvent<HTMLButtonElement, MouseEvent>) => {
        e.preventDefault();
        e.stopPropagation();
        copyToClipboard(code);
        sendSuccessNotification("Copied to clipboard!");
    }, [code]);

    if (inline === true) return <code>{children}</code>;

    return <div className={Style}>
        <SyntaxHighlighter
            language={l}
            style={atomOneDark}
            customStyle={{
                margin: 0,
                padding: "16px 56px 16px 16px",
                borderRadius: 8,
                maxHeight: maxHeight || undefined,
                overflow: "auto",
                fontFamily: "var(--monospace)",
            }}
        >
            {code}
        </SyntaxHighlighter>
        <button onClick={doCopy}>
            <TooltipV2 tooltip={"Copy"}>
                <Icon cursor="pointer" name={"heroClipboardDocument"} color={"fixedWhite"} />
            </TooltipV2>
        </button>
    </div>;
}

function codeSnippetText(children: React.ReactNode): string {
    if (children == null || typeof children === "boolean") return "";
    if (typeof children === "string" || typeof children === "number") return String(children);
    if (Array.isArray(children)) return children.map(codeSnippetText).join("");
    if (React.isValidElement(children)) {
        return codeSnippetText((children.props as {children?: React.ReactNode}).children);
    }
    return "";
}

function codeSnippetLanguage(children: React.ReactNode): string | undefined {
    if (!React.isValidElement(children)) return undefined;
    const className = (children.props as {className?: string}).className ?? "";
    const match = /(?:^|\s)language-([^\s]+)/.exec(className);
    return match?.[1];
}

export default CodeSnippet;
