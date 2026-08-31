import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {MarkdownDocument} from "@/ui-components/Markdown";
import helpText from "./InvocationHelp.md?raw";

export function InvocationHelp(): React.ReactNode {
    return (
        <div className={InvocationHelpClass}>
            <MarkdownDocument text={helpText} />
        </div>
    );
}

const InvocationHelpClass = injectStyle("creator-invocation-help", k => `
    ${k} {
        --creator-help-inline-code-background: var(--secondaryMain);
        display: flex;
        justify-content: center;
        height: 100%;
        min-height: 0;
        overflow-y: auto;
        padding: 0 16px 8px;
        box-sizing: border-box;
        font-variant-ligatures: none;
        font-feature-settings: "liga" 0, "calt" 0;
    }

    html.dark ${k} {
        --creator-help-inline-code-background: #3d4148;
    }

    ${k} > div {
        width: 100%;
        max-width: 1100px;
        margin: 16px auto 0;
    }

    ${k} > div :not(pre) > code {
        background: var(--creator-help-inline-code-background);
        border-radius: 6px;
        padding: .2em .4em;
        font-size: 85%;
        font-variant-ligatures: none;
        font-feature-settings: "liga" 0, "calt" 0;
    }
`);
