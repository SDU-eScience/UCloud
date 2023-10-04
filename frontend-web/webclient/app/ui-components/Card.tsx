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
        border-radius: 25px;
        background: var(--appCard);
        border: 1px solid #E2DDDD;
        padding: 0 25px 40px 25px;
        box-shadow: 0px 3px 6px rgba(0, 0, 0, 16%);
    }
    
    html.dark ${k} {
        box-shadow: 0px 3px 6px rgba(255, 255, 255, 16%);
        border: 1px solid transparent;
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
