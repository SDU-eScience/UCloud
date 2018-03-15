import React from "react";
import {Pager} from "react-bootstrap";

export const PaginationButtons = (props) => {
    const totalPages = Math.ceil(props.totalEntries / props.entriesPerPage);
    if (totalPages < 2) {
        return null;
    }
    let pagination = [...Array(totalPages).keys()].map(i =>
        <Pager.Item href="#" onClick={() => props.toPage(i)} disabled={i === props.currentPage} key={i}>
            {i + 1}
        </Pager.Item>);
    return (
        <Pager>
            <Pager.Item disabled={props.currentPage === 0} onClick={() => props.toPage(props.currentPage - 1)}>
                {"<"}
            </Pager.Item>
            {pagination}
            <Pager.Item disabled={props.currentPage === totalPages - 1} onClick={() => props.toPage(props.currentPage + 1)}>
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