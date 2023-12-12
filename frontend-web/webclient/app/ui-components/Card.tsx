import * as React from "react";
import {
    BorderColorProps,
    BorderProps,
    BorderRadiusProps,
    BoxShadowProps,
    HeightProps,
    MinHeightProps,
    PaddingProps
} from "styled-system";
import {BoxProps} from "./Box";
import {classConcat, injectStyle, unbox} from "@/Unstyled";
import theme from "./theme";

export interface CardProps extends HeightProps,
    BoxProps,
    BorderColorProps,
    BoxShadowProps,
    BorderProps,
    BorderRadiusProps,
    PaddingProps,
    MinHeightProps {
    borderWidth?: number | string;
    children?: React.ReactNode;
    onClick?: (e: React.SyntheticEvent) => void;
    onContextMenu?: (e: React.SyntheticEvent) => void;
    className?: string;
}

export const CardClass = injectStyle("card", k => `
    ${k} {
        border-radius: 10px;
        box-shadow: ${theme.shadows.sm};
        border: 1px solid var(--midGray);
        background-color: var(--lightGray);
        color: var(--text);
        padding: 10px 15px;
    }

    html.dark ${k} {
        border: 1px solid var(--lightGray);
    }

    a ${k}:hover {
        border-color: var(--primary);
        transition: transform ${theme.timingFunctions.easeOut} ${theme.duration.fastest} ${theme.transitionDelays.xsmall};
    }
`);

export const Card: React.FunctionComponent<CardProps> = props => {
    return <div
        style={unbox(props)}
        className={classConcat(CardClass, props.className)}
        onClick={props.onClick}
        onContextMenu={props.onContextMenu}
        children={props.children}
    />;
};

Card.displayName = "Card";
export default Card;
