import * as React from "react";
import styled from "styled-components";
import Flex from "./Flex";
import Icon, {IconName} from "./Icon";
import {Spacer} from "./Spacer";
import Text from "./Text";

/* https://www.w3schools.com/howto/tryit.asp?filename=tryhow_js_accordion_symbol */
export function Accordion(props: React.PropsWithChildren<{icon?: IconName; title: React.ReactNode; titleContent?: React.ReactNode; forceOpen?: boolean;}>): JSX.Element {
    const [open, setOpen] = React.useState(false);
    const isOpen = props.forceOpen || open;
    return (
        <>
            <AccordionStyle className="AccordionStyle" active={isOpen} onClick={() => setOpen(!open)}>
                <Spacer
                    left={<>
                        {props.icon ? <Icon mr="12px" color="text" name={props.icon} /> :
                            <RotatingIcon color="text" size={15} mt="6px" name="chevronDown" rotation={open ? 0 : -90} />
                        }
                        <Text color="text">{props.title}</Text>
                    </>}
                    right={<Flex width="auto">{props.titleContent}</Flex>}
                />
            </AccordionStyle>
            <Panel active={isOpen}>
                {props.children}
            </Panel>
        </>
    );
}

const AccordionStyle = styled.div<{active: boolean}>`
    background-color: var(--white);
    padding: 18px;
    width: 100%;
    border: none;
    text-align: left;
    outline: none;
    font-size: 15px;
    cursor: pointer;
    ${p => p.active ? null : "border-bottom: solid lightGray 1px;"}
`;

const Panel = styled.div<{active: boolean}>`
    display: ${props => props.active ? "block" : "none"};
    max-height: ${props => props.active ? "auto" : 0};
    overflow: hidden;
    transition: all 0.2s ease-out;
`;

export const AccordionWrapper = styled.div`
    box-shadow: ${p => p.theme.shadows.md};
    border-bottom: 0px solid red;
`;

const RotatingIcon = styled(Icon)`
    size: 14px;
    margin-right: 12px;
    cursor: pointer;
    transition: transform 0.2s;
`;
