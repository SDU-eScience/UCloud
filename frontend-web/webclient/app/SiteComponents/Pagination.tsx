import * as React from "react";
import { Pagination, Dropdown } from "semantic-ui-react";
import { createRange } from "../UtilityFunctions";


interface PaginationButtons { totalPages: number, toPage: Function, currentPage: number }
export const PaginationButtons = ({ totalPages, toPage, currentPage }: PaginationButtons) => (
    totalPages > 1 ?
        (<Pagination
            totalPages={totalPages}
            activePage={currentPage + 1}
            onPageChange={(e, u) => toPage(u.activePage as number - 1)}
        />) : null
);



const EntriesPerPageSelectorOptions = [
    { key: 1, text: "10", value: 10 },
    { key: 2, text: "25", value: 25 },
    { key: 3, text: "50", value: 50 },
    { key: 4, text: "100", value: 100 }
]
interface EntriesPerPageSelector { entriesPerPage: number, onChange: Function, totalPages: number, children: React.ReactNode }
export const EntriesPerPageSelector = ({ entriesPerPage, onChange, totalPages, children }: EntriesPerPageSelector) => (
    <div>
        <Dropdown
            onChange={(e, { value }) => onChange(value as number)}
            options={EntriesPerPageSelectorOptions}
            selection
            value={entriesPerPage}
        /> {children}
    </div>
);