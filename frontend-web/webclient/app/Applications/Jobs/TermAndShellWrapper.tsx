import {classConcat, injectStyle} from "@/Unstyled";
import {isLightThemeStored} from "@/UtilityFunctions";
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
    }

    ${k}.light {
        background: #ffffff;
    }

    ${k}.dark {
        background: #282a36;
    }

    ${k} > .contents {
        width: 100%;
        height: 100%;
    }

    ${k} > .warn {
        position: fixed;
        bottom: 0;
        left: var(--currentSidebarStickyWidth);
        z-index: 1000000;
        width: calc(100vw - var(--currentSidebarStickyWidth));
        display: flex;
        padding: 16px;
        align-items: center;
        background: black;
        color: white;
    }

    ${k}[data-add-padding="true"] {
        padding: 16px;
    }
`);
