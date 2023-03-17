import * as React from "react";
import {injectStyle} from "@/Unstyled";

// https://www.w3schools.com/howto/howto_css_breadcrumbs.asp

export const BreadCrumbsClass = injectStyle("breadcrumbs", k => `
    ${k} {
        display: flex;
        width: calc(100% - 180px);
    }
    
    ${k}[data-embedded="true"] {
        width: calc(100% - 50px);
    }
    
    ${k} > span {
        font-size: 25px;
        display: inline-block;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
        
        color: var(--text, #f00);
        text-decoration: none;
    }
    
    ${k} > span:hover {
        cursor: pointer;
        color: var(--blue);
    }
    
    ${k} > span + span:before {
        padding: 0 8px;
        vertical-align: top;
        color: var(--text, #f00);
        content: "/";
    }
    
    ${k}.isMain > span:last-child:hover {
        color: var(--text, #f00);
        cursor: default;
    }
`);

export const BreadCrumbsBase: React.FunctionComponent<{
    embedded?: boolean;
    children?: React.ReactNode;
    className?: string;
}> = props => {
    return <div
        className={BreadCrumbsClass + " " + (props.className ?? "")}
        data-embedded={props.embedded === true}
        children={props.children}
    />;
}
