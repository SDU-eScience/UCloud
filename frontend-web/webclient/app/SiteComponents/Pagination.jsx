import React from "react";
import { Pager } from "react-bootstrap";

export default function PaginationButtons(props) {
    let pagination = [...Array(props.totalPages()).keys()].map(i =>
        <Pager.Item href="#" onClick={() => props.toPage(i)} disabled={i === props.currentPage} key={i}>
            {i + 1}
        </Pager.Item>);
    return (
        <Pager>
            <Pager.Item disabled={props.currentPage === 0} onClick={() => props.previousPage()}>
                {"<"}
            </Pager.Item>
            {pagination}
            <Pager.Item disabled={props.currentPage === props.totalPages() - 1} onClick={() => props.nextPage()} >
                {">"}
            </Pager.Item>
        </Pager>);
}