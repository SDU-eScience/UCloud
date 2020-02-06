import * as React from "react";
import styled from "styled-components";
import Box from "./Box";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";

interface ProgressBaseProps {
    height?: number | string;
    value?: number | string;
    label?: string;
}

const ProgressBase = styled(Box) <ProgressBaseProps>`
    border-radius: 5px;
    background-color: var(--${p => p.color}, #f00);
    height: ${props => props.height};
`;

const ProgressPulse = styled(ProgressBase) <{active: boolean}>`
    ${p => p.active ? "" : "display: none;"}
    height: 100%;
    /* From semantic-ui-css */
    animation: progress-active 2s ease infinite;
    color: black;

    @keyframes progress-active {
        0% {
            opacity: 0.3;
            width: 0;
        }
        100% {
            opacity: 0;
            width: 100%;
        }
    }
`;

ProgressBase.defaultProps = {
    color: "green",
    height: "30px",
};

interface Progress {
    color: ThemeColor;
    percent: number;
    active: boolean;
    label: string;
}

const Progress = ({color, percent, active, label}: Progress): JSX.Element => (
    <>
        <ProgressBase height="30px" width="100%" color="lightGray">
            <ProgressBase height="30px" color={color} width={`${percent}%`}>
                <ProgressPulse active={active} width="100%" />
            </ProgressBase>
        </ProgressBase>
        {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
    </>
);

ProgressBase.displayName = "ProgressBase";

export default Progress;
