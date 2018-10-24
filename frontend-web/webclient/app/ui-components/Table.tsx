import styled from "styled-components";
import { textAlign, TextAlignProps } from "styled-system";

const Table = styled.table`
    width: 100%;
`;

export const TableBody = styled.tbody``;

export const TableRow = styled.tr`
    & > td {
        border-top: 1px solid rgba(34,36,38,.1);
        padding-top: 11px;
        padding-bottom: 11px;
    }
`;

export const TableCell = styled.td<TextAlignProps>`
    ${textAlign}
`;

export const TableHeader = styled.thead`
    padding-top: 11px;
    padding-bottom: 11px;
`;

export const TableHeaderCell = styled.th<TextAlignProps>`
    ${textAlign}
`;


export default Table;