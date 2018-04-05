import React from "react";
import { Pager } from "react-bootstrap";
import { createRange } from "../UtilityFunctions";


export const PaginationButtons = ({ totalPages, toPage, currentPage }) => {
    if (totalPages === 0) { return null; }
    const pagination = createRange(totalPages).map(i =>
        <Pager.Item href="#" onClick={() => toPage(i)} disabled={i === currentPage} key={i}>
            {i + 1}
        </Pager.Item>);
    return (
        <Pager>
            <Pager.Item disabled={currentPage === 0} onClick={() => toPage(currentPage - 1)}>
                {"<"}
            </Pager.Item>
            {pagination}
            <Pager.Item disabled={currentPage + 1 === totalPages} onClick={() => toPage(currentPage + 1)}>
                {">"}
            </Pager.Item>
        </Pager>);
};

export const EntriesPerPageSelector = ({ entriesPerPage, handlePageSizeSelection, totalPages, children }) => (
    <span>
        <select value={entriesPerPage} onChange={(e) => handlePageSizeSelection(parseInt(e.target.value))}>
            <option value="10">10</option>
            <option value="25">25</option>
            <option value="50">50</option>
            <option value="100">100</option>
        </select> {children}
    </span>
);