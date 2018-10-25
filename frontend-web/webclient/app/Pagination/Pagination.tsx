import * as React from "react";
import { Pagination, Dropdown } from "semantic-ui-react";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Icon, Box, Text, Flex } from "ui-components";
import { DropdownContent } from "ui-components/Dropdown";

interface PaginationButtons {
    totalPages: number,
    toPage: Function,
    currentPage: number,
    as?: string
    className?: string
}

export const Buttons = ({ className, totalPages, toPage, currentPage, as }: PaginationButtons) =>
    totalPages > 1 ?
        (<Pagination
            as={as}
            className={className}
            totalPages={Math.max(1, totalPages)}
            activePage={currentPage + 1}
            onPageChange={(e, u) => toPage(u.activePage as number - 1)}
        />) : null;

const EntriesPerPageSelectorOptions = [
    { key: 1, text: "10", value: 10 },
    { key: 2, text: "25", value: 25 },
    { key: 3, text: "50", value: 50 },
    { key: 4, text: "100", value: 100 }
]

interface EntriesPerPageSelector {
    entriesPerPage: number,
    onChange: (size: number) => void,
    content?: string

    as?: string
    className?: string
}

export const EntriesPerPageSelector = ({
    entriesPerPage,
    onChange,
    content
}: EntriesPerPageSelector) => (
        <ClickableDropdown left={"55px"} minWidth={"80px"} width={"80px"}
            trigger={<Flex><Box> {`${content} ${entriesPerPage}`}</Box><Box><Icon name="chevronDown" /></Box></Flex>}>
            {EntriesPerPageSelectorOptions.map((opt, i) =>
                <Box ml="-17px" mr="-17px" key={i} onClick={() => entriesPerPage === opt.value ? undefined : onChange(opt.value)}>
                    <Text textAlign="center">{opt.text}</Text>
                </Box>
            )}
        </ClickableDropdown>
    );