import * as React from "react";
import {Button, Flex, Input, Text} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {TextSpan} from "@/ui-components/Text";
import {injectStyle} from "@/Unstyled";

const EntriesPerPageSelectorOptions = [
    {key: 1, text: "10", value: 10},
    {key: 2, text: "25", value: 25},
    {key: 3, text: "50", value: 50},
    {key: 4, text: "100", value: 100}
];

const handleBoundaries = (page: number, maxPage: number): number =>
    Math.max(Math.min(page, maxPage - 1), 0);

interface PaginationButtonsProps {totalPages: number; currentPage: number; toPage: (p: number) => void}
export function PaginationButtons({totalPages, currentPage, toPage}: PaginationButtonsProps): React.ReactNode {
    const ref = React.useRef<HTMLInputElement>(null);
    if (totalPages <= 1) return null;
    const inputField = (
        <Flex ml="15px" width="75px">
            {totalPages > 20 ? (
                <>
                    <Input defaultValue="1" autoComplete="off" type="number" min={"1"} max={totalPages} inputRef={ref} />
                    <Button
                        ml="2px"
                        fullWidth
                        onClick={() => {
                            const value = parseInt(ref.current?.value ?? "1", 10) - 1;
                            if (value < 0 || value > totalPages - 1) return;
                            toPage(handleBoundaries(value, totalPages));
                        }}
                    >
                        →
                    </Button>
                </>
            ) : null}
        </Flex>
    );
    const half = Math.floor((totalPages - 1) / 2);
    const upperQuarter = Math.floor(half + half / 2);
    const lowerQuarter = Math.floor(half - half / 2);
    const pages = [...new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1, half, upperQuarter, lowerQuarter].sort((a, b) => a - b))];

    const buttons = pages.filter(i => i >= 0 && i < totalPages).map((it, i, arr) =>
        it - arr[i + 1] < -1 ? ( // If the two numbers do not immediately follow each other, insert ellipses
            <React.Fragment key={it}>
                <PaginationButton onClick={() => toPage(it)}>{it + 1}</PaginationButton>
                <PaginationButton onClick={() => undefined} unclickable>...</PaginationButton>
            </React.Fragment>
        ) : (
            <PaginationButton
                key={it}
                unclickable={currentPage === it}
                color={currentPage === it ? "textSecondary" : "primaryMain"}
                onClick={() => toPage(it)}
            >
                {it + 1}
            </PaginationButton>
        )
    );
    return (
        <Flex className={PaginationGroupClass} justifyContent="center" my="1em">
            <PaginationButton
                onClick={() => toPage(currentPage - 1)}
                unclickable={currentPage === 0}
            >
                ⟨
            </PaginationButton>
            {buttons}
            <PaginationButton
                onClick={() => toPage(currentPage + 1)}
                unclickable={currentPage === totalPages - 1}
            >
                ⟩
            </PaginationButton>
            {inputField}
        </Flex>
    );
}


const PaginationButtonBaseClass = injectStyle("pagination-button", k => `
    ${k} {
        color: var(--textPrimary, #f00);
        background-color: transparent;
        border-color: var(--borderColor, #f00);
        border-width: 1px;
        border-right-width: 0px;
    }
    
    ${k}[data-unclickable="true"] {
        background-color: var(--textDisabled, #f00);
    }
    
    ${k}:disabled {
        opacity: 1;
    }
    
    ${k}:hover {
        filter: brightness(100%);
        transform: none;
    }

    ${k}[data-unclickable="true"]:hover {
        cursor: default;
    }
`);


function PaginationButton({onClick, ...props}): React.ReactNode {
    return (
        props.unclickable ?
            <Button data-unclickable className={PaginationButtonBaseClass} {...props} /> :
            <Button className={PaginationButtonBaseClass} onClick={onClick} {...props} />
    );
}

const PaginationGroupClass = injectStyle("pagination-group", k => `
    ${k} > ${PaginationButtonBaseClass} {
        width: auto;
        min-width: 42px;

        padding-left: 0px;
        padding-right: 0px;
        border-radius: 0px;
    }

    ${k} > ${PaginationButtonBaseClass}:nth-last-child(2) {
        border-radius: 0 3px 3px 0;
        border-right-width: 1px;
    }

    ${k} > ${PaginationButtonBaseClass}:first-child {
        border-radius: 3px 0 0 3px;
    }
`);

interface EntriesPerPageSelectorProps {
    entriesPerPage: number;
    onChange: (size: number) => void;
    content?: string;
}

export const EntriesPerPageSelector = ({
    entriesPerPage,
    onChange,
    content
}: EntriesPerPageSelectorProps): React.ReactNode => (
    <ClickableDropdown
        left="85px"
        minWidth="80px"
        width="80px"
        chevron
        trigger={<TextSpan> {`${content ?? "Entries per page"} ${entriesPerPage}`}</TextSpan>}
    >
        {EntriesPerPageSelectorOptions.map((opt, i) => (
            <Text
                cursor="pointer"
                key={i}
                onClick={() => entriesPerPage === opt.value ? undefined : onChange(opt.value)}
            >
                {opt.text}
            </Text>
        ))}
    </ClickableDropdown>
);
