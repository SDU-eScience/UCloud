import {injectStyle} from "@/Unstyled";
import {CSSProperties} from "react";
import * as React from "react";

const StickyBoxClass = injectStyle("sticky-box", k => `
    ${k} {
        position: sticky;
        background: var(--backgroundDefault, #f00);

        margin-left: calc(-1px * var(--normalMarginX, 0px));
        padding-left: var(--normalMarginX, 0px);
        padding-right: var(--normalMarginX, 0px);
        width: calc(100% + var(--normalMarginX, 0px) * 2);
        
        padding: 20px 0;
        z-index: 1000;
        top: -20px;
    } 
    
    ${k}[data-shadow="true"] {
        box-shadow: 0 1px 5px 0 rgba(0, 0, 0, 0.2);
    }
`);

export const StickyBox: React.FunctionComponent<{
    shadow?: boolean;
    normalMarginX?: string;
    children?: React.ReactNode;
}> = props => {
    const style: CSSProperties = {};
    style["--normalMarginX"] = props.normalMarginX ?? "0px";
    return <div className={StickyBoxClass} style={style} data-shadow={props.shadow === true}
                children={props.children} />;
}
