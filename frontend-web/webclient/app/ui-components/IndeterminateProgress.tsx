import * as React from "react";
import styled from "styled-components";
import Box from "./Box";
import Flex from "./Flex";
import Text from "./Text";
import {ThemeColor} from "./theme";

interface ProgressBaseProps {
    height?: number | string;
    value?: number | string;
    active?: boolean;
}

const ProgressBase = styled(Box) <ProgressBaseProps>`
    border-radius: 5px;
    background-color: var(--lightGray, #foo);
    height: ${props => props.height};
    position: relative;
    overflow: hidden;

    @keyframes movingbox {
        from {
            left: -120px;
        }

        to {
            left: 100%;
        }
    }

    &:after {
        content: " ";
        display: block;
        width: 120px;

        border-radius: 5px;
        position: absolute;
        height: 100%;

        z-index: 2;
        background-color: var(--${p => p.color}, #f00);

        animation: movingbox 3s linear infinite;
    }
`;

ProgressBase.defaultProps = {
    color: "green",
    height: "30px",
    active: false
};

interface Progress {
    color: ThemeColor;
    label: string;
}

const Progress = ({color, label}: Progress): JSX.Element => (
    <>
        <ProgressBase height="30px" width="100%" color={color} />
        {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
    </>
);

ProgressBase.displayName = "ProgressBase";

export default Progress;
