import * as React from "react";
import * as Pager from "react-bootstrap/lib/Pager";
import { createRange } from "../UtilityFunctions";
import { SSL_OP_CIPHER_SERVER_PREFERENCE } from "constants";


interface PaginationButtons { totalPages: number, toPage: Function, currentPage: number }
export const PaginationButtons = ({ totalPages, toPage, currentPage }: PaginationButtons) => {
    if (totalPages <= 1) { return null; }
    const paginationButtons = createPaginationButtons(totalPages, currentPage, (i: number) => toPage(i));
    return (
        <Pager>
            <Pager.Item disabled={currentPage === 0} onClick={() => toPage(currentPage - 1)}>
                {"<"}
            </Pager.Item>
            {paginationButtons}
            <Pager.Item disabled={currentPage + 1 === totalPages} onClick={() => toPage(currentPage + 1)}>
                {">"}
            </Pager.Item>
        </Pager>);
};

interface EntriesPerPageSelector { entriesPerPage: number, handlePageSizeSelection: Function, totalPages: number, children: React.ReactNode }
export const EntriesPerPageSelector = ({ entriesPerPage, handlePageSizeSelection, totalPages, children }: EntriesPerPageSelector) => (
    <span>
        <select value={entriesPerPage} onChange={(e) => handlePageSizeSelection(parseInt(e.target.value))}>
            <option value="10">10</option>
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
        </select> {children}
    </span>
);

const MAX_PAGES = 8;
const createPaginationButtons = (totalPages: number, currentPage: number, toPage: (n: number) => void) => {
    if (totalPages > MAX_PAGES) {
        // Create buttons based on current page. Remove those already being generated in the following code.
        let currentPages = [currentPage - 1, currentPage, currentPage + 1].filter((i) => i >= 3 && i < totalPages - 3);
        
        if (currentPage <= 4) {
            // Case one: Overlaps with initial three button. Append Ellipses (-1)
            currentPages = [...currentPages, -1];
        } else if (currentPage >= totalPages - 5) {
            // Case two: Overlaps with final three buttons. Prepend Ellipses
            currentPages = [-1, ...currentPages];
        } else {
            // Case three: Overlaps with neither. Prepend and Append Ellipses
            currentPages = [-1, ...currentPages, -1]
        }
        const final = [totalPages - 3, totalPages - 2, totalPages - 1];
        return [...[0, 1, 2], ...currentPages, ...final].map((i, index) =>
            <Pager.Item href="#" onClick={() => toPage(i)} disabled={i === currentPage || i < 0} key={index}>
                {i >= 0 ? i + 1 : "..."}
            </Pager.Item>);
    } else {
        return createRange(totalPages).map(i =>
            <Pager.Item href="#" onClick={() => toPage(i)} disabled={i === currentPage} key={i}>
                {i + 1}
            </Pager.Item>);
    }
}