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

import {classConcat, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {BoxProps} from "./Types";

export interface CardProps extends HeightProps,
    BoxProps,
    BorderColorProps,
    BoxShadowProps,
    BorderProps,
    BorderRadiusProps,
    PaddingProps,
    MinHeightProps {
    borderWidth?: number | string;
    children?: React.ReactNode; onClick?: (e: React.SyntheticEvent) => void;
    onContextMenu?: (e: React.SyntheticEvent) => void;
    className?: string;
    style?: CSSProperties;
}

export const CardClass = injectStyle("card", k => `
    ${k} {
        border-radius: 10px;
        box-shadow: var(--defaultShadow);
        // border: 1px solid var(--backgroundCardBorder);
        background-color: var(--backgroundCard);
        color: var(--textPrimary);
        padding: 20px;
    }
`);

export const Card: React.FunctionComponent<CardProps> = props => {
    return <div
        style={{...unbox(props), ...(props.style ?? {})}}
        className={classConcat(CardClass, props.className)}
        onClick={props.onClick}
        onContextMenu={props.onContextMenu}
        children={props.children}
    />;
};

Card.displayName = "Card";
export default Card;
