import React from "react";
import {Pager} from "react-bootstrap";

export const PaginationButtons = ({ totalPages, toPage, currentPage }) => {
    if (totalPages < 1) {
        return null;
    }
    let pagination = [...Array(totalPages).keys()].map(i =>
        <Pager.Item href="#" onClick={() => toPage(i)} disabled={i === currentPage} key={i}>
            {i + 1}
        </Pager.Item>);
    return (
        <Pager>
            <Pager.Item disabled={currentPage === 0} onClick={() => toPage(currentPage - 1)}>
                {"<"}
            </Pager.Item>
            {pagination}
            <Pager.Item disabled={currentPage === totalPages - 1} onClick={() => toPage(currentPage + 1)}>
                {">"}
            </Pager.Item>
        </Pager>);
};


export const EntriesPerPageSelector = ({entriesPerPage, handlePageSizeSelection}) => (
    <select value={entriesPerPage} onChange={e => handlePageSizeSelection(parseInt(e.target.value))}>
        <option value="10">10</option>
        <option value="25">25</option>
        <option value="50">50</option>
        <option value="100">100</option>
    </select>
);