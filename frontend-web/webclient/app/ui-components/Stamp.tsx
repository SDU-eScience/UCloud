import * as React from "react";
import {Box, Icon, Text} from ".";
import {IconName} from "./Icon";
import {injectStyle} from "@/Unstyled";

const StampClass = injectStyle("stamp", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        vertical-align: top;
        min-height: 24px;
        font-weight: 600;
        letter-spacing: 0.025em;
        border-radius: 4px;
        border-width: 1px;
        border-style: solid;
        cursor: pointer;
        
        padding: 0 1px;
        margin-right: 4px;
        
        background-color: var(--lightBlue);
        border-color: var(--lightBlue);
        color: var(--darkBlue);
    }
`);

interface StampProps {
    children?: React.ReactNode;
}

const Stamp: React.FunctionComponent<StampProps & {icon?: IconName; onClick?: () => void; text?: string}> = (props) =>
    <div className={StampClass} onClick={props.onClick}>
        {props.icon ? <Icon name={props.icon} size={12} /> : null}
        <Text ml="4px" mr="6px">{props.text}{props.children}</Text>
        <Box flexGrow={1} />
        {props.onClick ? <Icon name={"close"} size={12} onClick={props.onClick} /> : null}
    </div>

export default Stamp;
