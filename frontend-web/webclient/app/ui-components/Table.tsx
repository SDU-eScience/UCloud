import {classConcat, extractDataTags, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import * as React from "react";
import {BoxProps} from "./Types";

export const TableClass = injectStyle("table", k => `
    ${k} {
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
`);

export const Table: React.FunctionComponent<BoxProps & {
    children?: React.ReactNode;
    className?: string;
}> = props => {
    return <table className={classConcat(TableClass, props.className)} style={unbox(props)} {...extractEventHandlers(props)}>
        {props.children}
    </table>;
};

Table.displayName = "Table";

export const TableCell: React.FunctionComponent<BoxProps & {children?: React.ReactNode;}> = props => {
    return <td style={unbox(props)} {...extractEventHandlers(props)}>{props.children}</td>;
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
