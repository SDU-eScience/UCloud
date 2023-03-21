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
import {injectStyleSimple, unbox} from "@/Unstyled";

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

export const CardClass = injectStyleSimple("card", () => `
    border-radius: 25px;
    background: #FAFBFC;
    border: 1px solid #E2DDDD;
    padding: 0 25px 25px 25px;
    box-shadow: 0px 3px 6px rgba(0, 0, 0, 16%);
`);

export const Card: React.FunctionComponent<CardProps> = props => {
    return <div
        style={unbox(props)}
        className={CardClass + " " + (props.className ?? "")}
        onClick={props.onClick}
        onContextMenu={props.onContextMenu}
        children={props.children}
    />;
};

Card.displayName = "Card";
export default Card;
