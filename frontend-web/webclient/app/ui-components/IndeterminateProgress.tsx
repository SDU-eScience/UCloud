import * as React from "react";
import styled from "styled-components";
import Box from "./Box";
import Flex from "./Flex";
import Text from "./Text";
import {default as theme, ThemeColor} from "./theme";

interface ProgressBaseProps {
    height?: number | string;
    value?: number | string;
    active?: boolean;
}

const ProgressBase = styled(Box)<ProgressBaseProps>`
    border-radius: 5px;
    background-color: ${props => props.theme.colors["lightGray"]};
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
        background-color: ${props => props.theme.colors[props.color!]};

        animation: movingbox 3s linear infinite;
    }
`;

ProgressBase.defaultProps = {
    color: "green",
    height: "30px",
    active: false,
    theme
};

interface Progress {
    color: ThemeColor;
    label: string;
}

const Progress = ({color, label}: Progress) => (
    <>
        <ProgressBase height={"30px"} style={{width: "100%"}} color={color} />
        {label ? <Flex justifyContent="center"><Text>{label}</Text></Flex> : null}
    </>
);

ProgressBase.displayName = "ProgressBase";

export default Progress;
