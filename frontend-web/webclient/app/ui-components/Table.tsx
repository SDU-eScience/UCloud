import styled from "styled-components";
import { textAlign, TextAlignProps } from "styled-system";
import { HideProps, hidden } from "./Hide";

const Table = styled.table`
    width: 100%;
    border: 0px;
    border-spacing: 0;
`;

export const TableBody = styled.tbody``;

export const TableRow = styled.tr`

    & > td {
        border-spacing: 0;
        border-top: 1px solid rgba(34,36,38,.1);
        padding-top: 11px;
        padding-bottom: 11px;
    }
`;

export const TableCell = styled.td<TextAlignProps & HideProps>`
    border: 0px;
    border-spacing: 0;
    ${textAlign};
    ${hidden("xs")} ${hidden("sm")} ${hidden("md")} ${hidden("lg")} ${hidden("xl")};
`;

export const TableHeader = styled.thead`
    padding-top: 11px;
    padding-bottom: 11px;
`;

export const TableHeaderCell = styled.th<TextAlignProps & HideProps>`
    border-spacing: 0;
    border: 0px;
    ${textAlign};
    ${hidden("xs")} ${hidden("sm")} ${hidden("md")} ${hidden("lg")} ${hidden("xl")};
`;


export default Table;