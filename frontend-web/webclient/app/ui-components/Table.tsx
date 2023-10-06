import {classConcat, extractDataTags, extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {BoxProps} from "@/ui-components/Box";
import * as React from "react";

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
        
        border-bottom: 1px solid rgba(34, 36, 38, .1);
        padding: 8px 0;
    }
    
    ${k} tr {
        cursor: auto;
    }
    
    ${k} tr[data-highlight="true"]:hover, ${k}[data-highlighted="true"] {
        background-color: var(--tableRowHighlight);
    }
    
    ${k} th {
        border-spacing: 0;
        border: 0;
    }
    
    ${k} thead {
        padding-top: 11px;
        padding-bottom: 11px;
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
