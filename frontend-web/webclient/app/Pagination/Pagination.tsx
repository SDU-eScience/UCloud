import * as React from "react";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Icon, Box, Text, Flex, Button, theme } from "ui-components";
import styled from "styled-components";

const EntriesPerPageSelectorOptions = [
    { key: 1, text: "10", value: 10 },
    { key: 2, text: "25", value: 25 },
    { key: 3, text: "50", value: 50 },
    { key: 4, text: "100", value: 100 }
];

interface PaginationButtons { totalPages: number, currentPage: number, toPage: (p: number) => void }
export function PaginationButtons({ totalPages, currentPage, toPage }: PaginationButtons) {
    if (totalPages <= 1) return null;
    const pages = [...new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1].sort((a, b) => a - b))];
    const buttons = pages.filter(i => i >= 0 && i < totalPages).map((it, i, arr) =>
        it - arr[i + 1] < -1 ? ( // If the two numbers do not immediately follow each other, insert ellipses
            <React.Fragment key={it}>
                <PaginationButton onClick={() => toPage(it)}>{it + 1}</PaginationButton>
                <PaginationButton onClick={() => undefined} unclickable>{"..."}</PaginationButton>
            </React.Fragment>
        ) : (
                <PaginationButton key={it} unclickable={currentPage === it} color={currentPage === it ? "gray" : "black"} onClick={() => toPage(it)}>{it + 1}</PaginationButton>
            )
    );
    return (
        <PaginationGroup>
            <PaginationButton onClick={() => toPage(0)} unclickable={currentPage === 0}>{"⟨⟨"}</PaginationButton>
            <PaginationButton onClick={() => toPage(currentPage - 1)} unclickable={currentPage === 0}>{"⟨"}</PaginationButton>
            {buttons}
            <PaginationButton onClick={() => toPage(currentPage + 1)} unclickable={currentPage === totalPages - 1}>{"⟩"}</PaginationButton>
            <PaginationButton onClick={() => toPage(totalPages)} unclickable={currentPage === totalPages - 1}>{"⟩⟩"}</PaginationButton>
        </PaginationGroup>
    );
};


const PaginationButtonBase = styled(Button) <{ unclickable?: boolean }>`
    color: ${props => props.theme.colors.text};
    background-color: ${props => props.unclickable ? props.theme.colors.paginationDisabled : "transparent"};
    border-color: ${props => props.theme.colors.borderGray};
    border-width: 1px;
    &:disabled {
        opacity: 1;
    }
    border-right-width: 0px;
    &:hover {
        filter: brightness(100%);
        background-color: ${props => props.unclickable ? null : props.theme.colors.paginationHoverColor};
        cursor: ${props => props.unclickable ? "default" : null};
    }
`;


const PaginationButton = ({ onClick, ...props }) => (
    props.unclickable ? <PaginationButtonBase {...props} /> : <PaginationButtonBase onClick={onClick} {...props} />
);

PaginationButtonBase.defaultProps = {
    theme
}

const PaginationGroup = styled(Flex)`
    & > ${PaginationButtonBase} {
        width: 42px;
        padding-left: 0px;
        padding-right: 0px;
        border-radius: 0px;
    }

    & > ${PaginationButtonBase}:last-child {
        border-radius: 0 3px 3px 0;
        border-right-width: 1px;
    }

    & > ${PaginationButtonBase}:first-child {
        border-radius: 3px 0 0 3px;
    }
`;

interface EntriesPerPageSelector {
    entriesPerPage: number,
    onChange: (size: number) => void,
    content?: string
}

export const EntriesPerPageSelector = ({
    entriesPerPage,
    onChange,
    content
}: EntriesPerPageSelector) => (
        <ClickableDropdown left={"85px"} minWidth={"80px"} width={"80px"}
            trigger={<Flex><Box> {`${content} ${entriesPerPage}`}</Box><Box><Icon name="chevronDown" /></Box></Flex>}>
            {EntriesPerPageSelectorOptions.map((opt, i) =>
                <Box ml="-17px" mr="-17px" key={i} onClick={() => entriesPerPage === opt.value ? undefined : onChange(opt.value)}>
                    <Text textAlign="center">{opt.text}</Text>
                </Box>
            )}
        </ClickableDropdown>
    );