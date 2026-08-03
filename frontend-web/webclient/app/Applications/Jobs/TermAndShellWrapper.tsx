import {classConcat, injectStyle} from "@/Unstyled";
import {isLightThemeStored} from "@/UtilityFunctions";
import {xtermThemes} from "@/Applications/Jobs/XTermLib";
import * as React from "react";

export function TermAndShellWrapper(props: React.PropsWithChildren<{addPadding: boolean}>) {
    return <div className={classConcat(TermAndShellWrapperClass, isLightThemeStored() ? "light" : "dark")} data-add-padding={props.addPadding}>
        {props.children}
    </div>
}


const TermAndShellWrapperClass = injectStyle("term-and-wrapper", k => `
    ${k} {
        display: flex;
        height: 100%;
        width: 100%;
        flex-direction: column;
        position: relative;
    }

    ${k}.light {
        background: ${xtermThemes.light.background};
    }

    ${k}.dark {
        background: ${xtermThemes.dark.background};
    }

    html.light ${k} {
        background: ${xtermThemes.light.background};
    }

    html.dark ${k} {
        background: ${xtermThemes.dark.background};
    }

    ${k} > .contents {
        width: 100%;
        height: 100%;
    }

    ${k} > .warn {
        position: absolute;
        top: 12px;
        right: 12px;
        z-index: 1;
        max-width: calc(100% - 24px);
        box-sizing: border-box;
        display: flex;
        padding: 4px 0;
        align-items: center;
        gap: 8px;
        font-family: "Jetbrains Mono", "Ubuntu Mono", courier-new, courier, monospace;
        font-size: 14px;
        line-height: 1.4;
        color: ${xtermThemes.light.foreground};
    }

    ${k}.dark > .warn,
    html.dark ${k} > .warn {
        color: ${xtermThemes.dark.foreground};
    }

    ${k} > .warn > .reconnect-button {
        border: 0;
        padding: 0;
        margin: 0;
        font: inherit;
        color: var(--primaryMain);
        background: transparent;
        cursor: pointer;
    }

    ${k}.dark > .warn > .reconnect-button,
    html.dark ${k} > .warn > .reconnect-button {
        color: var(--primaryLight);
    }

    ${k} > .warn > .reconnect-button:hover {
        color: var(--primaryLight);
        text-decoration: underline;
    }

    ${k} > .warn > .reconnect-button:focus-visible {
        outline: 1px solid var(--primaryMain);
        outline-offset: 2px;
    }

    ${k}[data-add-padding="true"] {
        padding: 16px;
    }
`);
