import {classConcat, extractDataTags, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import * as React from "react";
import {BoxProps} from "./Types";

export const TableClass = injectStyle("table", k => `
    ${k} table {
        border: 0;
        border-spacing: 0;
        table-layout: fixed;
        min-width: 15em;
        width: 100%;
    }
    
    ${k} td {
        border: 0;
        border-spacing: 0;
        padding: 8px 0;
    }
    
    ${k} tr {
        border-top: 0.5px solid var(--borderColor);
        cursor: auto;
    }

    ${k} tr:first-of-type {
        border-top: 0px;
    }
    
    ${k} tr[data-highlight="true"]:hover, ${k}[data-highlighted="true"] {
        background-color: var(--primaryLight);
    }
    
    ${k} th {
        border-spacing: 0;
        border: 0;
    }
    
    html,
    html.light {
        --tableBackground: var(--blue-10);
    }
    
    html.dark {
        --tableBackground: #282c34;
    }
    
    ${k}.presentation {
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        margin-top: 12px;
        margin-bottom: 12px;
    }
    
    ${k}.presentation table {
        text-align: left;
        border-collapse: collapse;
        width: 100%;
    }
    
    ${k}.presentation tr {
        border-top: 1px solid var(--borderColor);
        border-bottom: 1px solid var(--borderColor);
        vertical-align: top;
    }
    
    ${k}.presentation th,
    ${k}.presentation td {
        border-left: 1px solid var(--borderColor);
        border-right: 1px solid var(--borderColor);
        padding: 12px;
    }
    
    ${k}.presentation th:first-child,
    ${k}.presentation td:first-child {
        border-left: 0;
    }
    
    ${k}.presentation th:last-child,
    ${k}.presentation td:last-child {
        border-right: 0;
    }
    
    ${k}.presentation tr:first-child {
        border-top: 0;
    }
    
    ${k}.presentation tbody tr:last-child {
        border-bottom: 0;
    }
    
    ${k}.presentation thead tr:first-child > td:first-child,
    ${k}.presentation thead tr:first-child > th:first-child {
        border-top-left-radius: 8px;
    }
    
    ${k}.presentation thead tr:first-child > td:last-child,
    ${k}.presentation thead tr:first-child > th:last-child {
        border-top-right-radius: 8px;
    }
    
    ${k}.presentation tbody tr:last-child {
        border-bottom: 0;
    }
    
    ${k}.presentation thead tr {
        background: var(--tableBackground);
    }
    
    ${k}.presentation tr p:first-child {
        margin-top: 0;
    }
    
    ${k}.presentation tr p:last-child {
        margin-bottom: 0;
    }
`);

export const Table: React.FunctionComponent<BoxProps & {
    children?: React.ReactNode;
    tableType?: "clean" | "presentation"
}> = props => {
    // NOTE(Dan): The presentation table is a slightly modified version of the table we use on our documentation page.
    // It is currently modified to have slightly less padding.

    return <div className={classConcat(TableClass, props.tableType)} style={unbox(props)} >
        <table {...extractEventHandlers(props)}>
            {props.children}
        </table>
    </div> ;
};

Table.displayName = "Table";

export const TableCell: React.FunctionComponent<BoxProps & {children?: React.ReactNode; colSpan?: number;}> = props => {
    return <td style={unbox(props)} {...extractEventHandlers(props)} colSpan={props.colSpan}>{props.children}</td>;
};

TableCell.displayName = "TableCell";

export const TableRow: React.FunctionComponent<BoxProps & {
    children?: React.ReactNode;
    highlightOnHover?: boolean;
    highlighted?: boolean;
    className?: string;
}> = props => {
    return <tr
        data-highlight={props.highlightOnHover === true}
        data-highlighted={props.highlighted === true}
        className={props.className}
        style={unbox(props)}
        {...extractEventHandlers(props)}
        {...extractDataTags(props as Record<string, string>)}
        children={props.children}
    />;
};

TableRow.displayName = "TableRow";

export const TableHeader: React.FunctionComponent<{children?: React.ReactNode;}> = props => {
    return <thead children={props.children} />;
}

TableHeader.displayName = "TableHeader";

export const TableHeaderCell: React.FunctionComponent<BoxProps & {children?: React.ReactNode;}> = props => {
    return <th style={unbox(props)} {...extractEventHandlers(props)} children={props.children} />;
}

TableHeaderCell.displayName = "TableHeaderCell";

export default Table;
