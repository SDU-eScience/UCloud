import styled from "styled-components";
import Box from "./Box";
import * as React from "react";
import Flex from "./Flex";
import Truncate from "./Truncate";

type StringOrNumber = string | number;

interface UseChildPaddingProps {
    childPadding?: StringOrNumber;
}

function useChildPadding(
    props: UseChildPaddingProps
): null | {marginBottom: StringOrNumber; marginTop: StringOrNumber} {
    return props.childPadding ? {marginBottom: props.childPadding, marginTop: props.childPadding} : null;
}

const List = styled(Box) <{fontSize?: string; childPadding?: string | number; bordered?: boolean}>`
    font-size: ${props => props.fontSize};
    & > * {
        ${props => props.bordered ? "border-bottom: 1px solid lightGrey;" : null}
        ${useChildPadding};
    }

    & > *:last-child {
        ${props => props.bordered ? "border-bottom: 0px;" : null}
    }
`;

List.defaultProps = {
    fontSize: "large",
    bordered: true
};

List.displayName = "List";

interface ListRowProps {
    isSelected: boolean;
    select: () => void;
    navigate: () => void;
    left: React.ReactNode;
    leftSub?: React.ReactNode;
    icon?: React.ReactNode;
    right: React.ReactNode;
}

export function ListRow(props: ListRowProps): JSX.Element {
    const left = props.leftSub ? (
        <Box mb="4px" onClick={e => {e.stopPropagation(); props.navigate();}}>
            <Truncate width={1} mb="-4px" fontSize={20}>{props.left}</Truncate>
            <Flex>
                {props.leftSub}
            </Flex>
        </Box>
    ) : props.left;
    return (
        <HoverColorFlex
            isSelected={props.isSelected}
            onClick={props.select}
            pt="4px"
            pb="4px"
            width="100%"
            alignItems="center"
        >
            {props.icon ? <Box mx="8px" mt="4px">{props.icon}</Box> : null}
            {left}
            <Box ml="auto" />
            <Flex mr="8px">
                {props.right}
            </Flex>
        </HoverColorFlex>
    );
}

const HoverColorFlex = styled(Flex)<{isSelected: boolean}>`
    transition: background-color 0.3s;
    background-color: var(--${p => p.isSelected ? "lightBlue" : "white"});
    &:hover {
        background-color: var(--lightBlue, #f00);
    }
`;

export default List;
