import * as React from "react";
import {injectStyle} from "@/Unstyled";
import Icon from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {Relative} from "@/ui-components/index";
import {useCallback, useRef} from "react";
import {copyToClipboard} from "@/UtilityFunctions";

const Style = injectStyle("code-snippet", k => `
    ${k} {
        display: flex;
        flex-direction: row;
    }
    
    ${k} pre {
        padding: 16px;
        border-radius: 8px;
        font-family: var(--monospace);
        overflow: scroll;
        background: #192330;
        color: #cdcecf;
        display: block;
        flex-grow: 1;
    }
    
    ${k} button {
        background: rgba(255, 255, 255, 30%);
        border-radius: 8px;
        padding: 8px;
        position: absolute;
        top: 22px;
        right: 16px;
        border: 0;
        outline: none;
    }
`);

const CodeSnippet: React.FunctionComponent<{children: React.ReactNode, maxHeight: string}> = ({children, maxHeight}) => {
    const preRef = useRef<HTMLPreElement>(null);
    const doCopy = useCallback((e: React.MouseEvent<HTMLButtonElement, MouseEvent>) => {
        e.preventDefault();
        e.stopPropagation();
        copyToClipboard({
            value: preRef.current?.textContent ?? "",
            message: "Copied to clipboard!"
        });
    }, []);

    return <div className={Style} style={{maxHeight}}>
        <pre ref={preRef}>{children}</pre>
        <Relative>
            <button onClick={doCopy}>
                <TooltipV2 tooltip={"Copy"}>
                    <Icon name={"heroClipboardDocument"} color={"fixedWhite"} />
                </TooltipV2>
            </button>
        </Relative>
    </div>;
}

export default CodeSnippet;