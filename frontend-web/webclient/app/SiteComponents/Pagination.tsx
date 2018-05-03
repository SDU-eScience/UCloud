import * as React from "react";
import * as Pager from "react-bootstrap/lib/Pager";
import { Pagination } from "semantic-ui-react";
import { createRange } from "../UtilityFunctions";


interface PaginationButtons { totalPages: number, toPage: Function, currentPage: number }
export const PaginationButtons = ({ totalPages, toPage, currentPage }: PaginationButtons) => (
    totalPages > 1 ? 
        (<Pagination 
            totalPages={totalPages}
            defaultActivePage={1}
            onPageChange={(e, u) => toPage(u.activePage as number - 1)}
        />) : null
);

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