import {stopPropagationAndPreventDefault} from "@/UtilityFunctions";
import * as React from "react";
import styled from "styled-components";
import {margin, MarginProps, padding, PaddingProps} from "styled-system";
import Box, {BoxProps} from "./Box";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import {Spacer} from "./Spacer";
import Text from "./Text";
import {ThemeColor} from "./theme";

/* https://www.w3schools.com/howto/tryit.asp?filename=tryhow_js_accordion_symbol */
export function Accordion(props: React.PropsWithChildren<{
    icon?: IconName;
    iconColor?: ThemeColor;
    iconColor2?: ThemeColor;
    title: React.ReactNode;
    titleContent?: React.ReactNode;
    titleContentOnOpened?: React.ReactNode;
    forceOpen?: boolean;
    noBorder?: boolean;
    omitChevron?: boolean;
    panelProps?: MarginProps & PaddingProps;
}>): JSX.Element {
    const color = props.iconColor ?? "text";
    const [open, setOpen] = React.useState(false);
    const isOpen = props.forceOpen || open;
    return (
        <>
            <AccordionStyle active={isOpen} noBorder={props.noBorder ?? false} onClick={() => setOpen(!open)}>
                <Spacer
                    left={<>
                        {props.icon ? <Icon color2={props.iconColor2} mr="12px" color={color} name={props.icon} /> :
                            props.omitChevron ? null : <RotatingIcon color="text" size={15} mt="6px" name="chevronDown" rotation={open ? 0 : -90} />
                        }
                        <Text color="text">{props.title}</Text>
                    </>}
                    right={<Flex onClick={stopPropagationAndPreventDefault} width="auto">{props.titleContent}{isOpen ? props.titleContentOnOpened : null}</Flex>}
                />
            </AccordionStyle>
            <Panel active={isOpen} noBorder={props.noBorder ?? false} {...props.panelProps}>
                {props.children}
            </Panel>
        </>
    );
}

/* FIXME(Jonas): `noBorder` is a workaround that should be handled purely by CSS. :last-child, for example. */
const AccordionStyle = styled.div<{active: boolean; noBorder: boolean}>`
    background-color: var(--white);
    padding: 18px;
    width: 100%;
    border: none;
    text-align: left;
    outline: none;
    font-size: 15px;
    cursor: pointer;
    border-bottom: solid lightGray 1px;
    ${p => p.noBorder && !p.active ? "border-bottom: solid lightGray 0px;" : null}
`;

/* FIXME(Jonas): `noBorder` is a workaround that should be handled purely by CSS. :last-child, for example. */
const Panel = styled.div<{active: boolean; noBorder: boolean;} & MarginProps & PaddingProps>`
    display: ${props => props.active ? "block" : "hidden"};
    ${props => props.active && !props.noBorder ? "border-bottom: 1px solid lightGray;" : null}
    max-height: ${props => props.active ? "auto" : 0};
    overflow: hidden;
    transition: all 0.2s ease-out;
    ${margin}
    ${padding}
`;

const RotatingIcon = styled(Icon)`
    size: 14px;
    margin-right: 12px;
    cursor: pointer;
    transition: transform 0.2s;
`;
