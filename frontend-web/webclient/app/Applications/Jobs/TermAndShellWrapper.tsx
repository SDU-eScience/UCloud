import {classConcat, injectStyle} from "@/Unstyled";
import {isLightThemeStored} from "@/UtilityFunctions";
import {xtermThemes} from "@/Applications/Jobs/XTermLib";
import {Feature, hasFeature} from "@/Features";
import {useBackendFeatures} from "@/Features/backend";
import {terminalThemes} from "@/TerminalEngine/themes";
import * as React from "react";

export function TermAndShellWrapper(props: React.PropsWithChildren<{addPadding: boolean}>) {
    useBackendFeatures();
    const isV2 = hasFeature(Feature.TERMINAL_V2);
    return <div
        className={classConcat(TermAndShellWrapperClass, isLightThemeStored() ? "light" : "dark")}
        data-add-padding={props.addPadding}
        data-terminal-v2={isV2}
    >
        {props.children}
    </div>;
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

    ${k}[data-terminal-v2="true"].light,
    html.light ${k}[data-terminal-v2="true"] {
        background: ${terminalThemes.light.background};
    }

    ${k}[data-terminal-v2="true"].dark,
    html.dark ${k}[data-terminal-v2="true"] {
        background: ${terminalThemes.dark.background};
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
        color: var(--linkColor);
        background: transparent;
        cursor: pointer;
    }

    ${k} > .warn > .reconnect-button:hover {
        color: var(--linkColorHover);
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
