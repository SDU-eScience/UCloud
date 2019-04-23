import styled from "styled-components";
import { textAlign, TextAlignProps, WidthProps, width, MinWidthProps, minWidth } from "styled-system";
import theme from "./theme";
import { Cursor } from "./Types";

export const Table = styled.table< WidthProps & MinWidthProps >`
    border: 0px;
    border-spacing: 0;
    table-layout: fixed;
    ${width} ${minWidth}
`;

Table.defaultProps = {
    width: "100%",
    minWidth: "15em"
}

export const TableBody = styled.tbody``;

export const TableCell = styled.td<TextAlignProps>`
    border: 0px;
    border-spacing: 0;
    ${textAlign};
`;

const highlighted = ({ highlighted }: { highlighted?: boolean }) => highlighted ? { backgroundColor: theme.colors.tableRowHighlight } : null;

const contentAlign = props => props.aligned ? { verticalAlign: props.aligned } : null;


export const TableRow = styled.tr<{ highlighted?: boolean, contentAlign?: string, cursor?: Cursor }>`
    ${highlighted};
    ${contentAlign};
    cursor: ${props => props.cursor};

    & > ${TableCell} {
        border-spacing: 0;
        border-top: 1px solid rgba(34,36,38,.1);
        padding-top: 11px;
        padding-bottom: 11px;
    }
`;

TableRow.defaultProps = {
    cursor: "auto"
}

export const TableHeader = styled.thead`
    padding-top: 11px;
    padding-bottom: 11px;
`;

export const TableHeaderCell = styled.th<TextAlignProps & WidthProps>`
    border-spacing: 0;
    border: 0px;
    ${textAlign};
    ${width} ${minWidth}
`;

export default Table;