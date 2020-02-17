import styled from "styled-components";
import {
    color,
    ColorProps,
    minWidth,
    MinWidthProps,
    textAlign,
    TextAlignProps,
    width,
    WidthProps
} from "styled-system";
import {Cursor} from "./Types";

export const Table = styled.table<WidthProps & MinWidthProps & ColorProps>`
    ${color}
    border: 0px;
    border-spacing: 0;
    table-layout: fixed;
    ${width} ${minWidth} ${color}
`;

Table.displayName = "Table";

Table.defaultProps = {
    backgroundColor: "white",
    width: "100%",
    minWidth: "15em"
};

export const TableCell = styled.td<TextAlignProps>`
    border: 0px;
    border-spacing: 0;
    ${textAlign}
`;

TableCell.displayName = "TableCell";

const isHighlighted = ({highlighted}: {highlighted?: boolean}): {backgroundColor: string} | null =>
    highlighted ? {backgroundColor: "--var(tableRowHighlight)"} : null;

export const TableRow = styled.tr<{highlighted?: boolean; contentAlign?: string; cursor?: Cursor} & ColorProps>`
    ${isHighlighted};
    cursor: ${props => props.cursor};

    & > ${TableCell} {
        border-spacing: 0;
        border-top: 1px solid rgba(34,36,38,.1);
        padding-top: 8px;
        padding-bottom: 8px;
    }
`;

TableRow.defaultProps = {
    backgroundColor: "white",
    cursor: "auto"
};

TableRow.displayName = "TableRow";

export const TableHeader = styled.thead`
    background-color: var(--white, #f00);
    padding-top: 11px;
    padding-bottom: 11px;
`;

TableHeader.displayName = "TableHeader";

export const TableHeaderCell = styled.th<TextAlignProps & WidthProps>`
    border-spacing: 0;
    border: 0px;
    ${textAlign};
    ${width} ${minWidth}
`;

TableHeaderCell.displayName = "TableHeaderCell";

export default Table;
