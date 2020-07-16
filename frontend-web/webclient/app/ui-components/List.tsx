import styled from "styled-components";
import Box from "./Box";
import * as React from "react";
import Flex from "./Flex";
import Truncate from "./Truncate";
import {stopPropagationAndPreventDefault} from "UtilityFunctions";
import {IconName} from "ui-components/Icon";
import {Icon, Text} from "ui-components/index";
import {ThemeColor} from "./theme";

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
    isSelected?: boolean;
    select?: () => void;
    navigate?: () => void;
    left: React.ReactNode;
    leftSub?: React.ReactNode;
    icon?: React.ReactNode;
    right: React.ReactNode;
}

export function ListRow(props: ListRowProps): JSX.Element {
    const isSelected = props.isSelected ?? false;
    const left = props.leftSub ? (
        <Box maxWidth="calc(100% - 180px)" width="auto">
            <Truncate
                cursor={props.navigate ? "pointer" : "default"}
                onClick={e => {props.navigate?.(); e.stopPropagation();}}
                mb="-4px"
                width={1}
                fontSize={20}
            >{props.left}</Truncate>
            <Flex mt="4px">
                {props.leftSub}
            </Flex>
        </Box>
    ) : props.left;
    return (
        <HoverColorFlex
            isSelected={isSelected}
            onClick={props.select}
            pt="5px"
            pb="5px"
            width="100%"
            alignItems="center"
        >
            {props.icon ? <Box onClick={stopPropagationAndPreventDefault} mx="8px" mt={props.leftSub ? "4px" : "-4px"}>{props.icon}</Box> : <Box width="4px" />}
            {left}
            <Box ml="auto" />
            <Flex mr="8px">
                {props.right}
            </Flex>
        </HoverColorFlex>
    );
}


export const ListRowStat: React.FunctionComponent<{icon?: IconName; color?: ThemeColor; color2?: ThemeColor}> = props => {
    const color: ThemeColor = props.color ?? "gray";
    const color2: ThemeColor = props.color2 ?? "white";
    return (
        <>
            <Text color="gray" fontSize={0} mr={"4px"}>
                {!props.icon ? null : (<>
                    <Icon size={"10"} color={color} color2={color2} name={props.icon} mt={"-2px"} />
                    {" "}
                </>)}
                {props.children}
            </Text>
        </>
    );
};

const HoverColorFlex = styled(Flex) <{isSelected: boolean}>`
    transition: background-color 0.3s;
    background-color: var(--${p => p.isSelected ? "lightBlue" : "white"});
    &:hover {
        background-color: var(--lightBlue, #f00);
    }
`;

export default List;
