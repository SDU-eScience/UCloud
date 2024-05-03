import * as React from "react";
import {classConcat, classConcatMult, extractEventHandlers, injectStyle, injectStyleSimple, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {FontWeightProps} from "styled-system";
import {BoxProps} from "./Types";

export interface TextProps extends BoxProps, FontWeightProps {
    align?: "left" | "right";
    caps?: boolean;
    regular?: boolean;
    italic?: boolean;
    bold?: boolean;
    selectable?: boolean;
    title?: string;
    style?: CSSProperties;
    className?: string;
}

export const TextClass = injectStyle("text", () => ``);

function extractCss(props: TextProps): CSSProperties {
    const style = {...unbox(props), ...(props.style ?? {})};
    if (props.selectable === false) style.userSelect = "none";
    if (props.caps === true) style.textTransform = "uppercase";
    if (props.regular === true) style.fontWeight = "normal";
    if (props.italic === true) style.fontStyle = "italic";
    if (props.align !== undefined) style.textAlign = props.align;
    if (props.cursor !== undefined) style.cursor = props.cursor;
    return style;
}

const Text: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <div className={classConcat(TextClass, props.className)} style={extractCss({cursor: props.cursor ?? "inherit", ...props})} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
};

export const TextDiv = Text;
export const TextSpan: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <span className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}
export const TextP: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <p className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

export const TextH1: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <h1 className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

export const TextH2: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <h2 className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

export const TextH3: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <h3 className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

export const TextH4: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <h4 className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

export const TextH5: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <h5 className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

export const TextH6: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <h6 className={classConcat(TextClass, props.className)} style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

const EllipsedTextClass = injectStyleSimple("ellipsed-text", `
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    display: inline-block;
    vertical-align: bottom;
`);

export const EllipsedText: React.FunctionComponent<React.PropsWithChildren<TextProps>> = props => {
    return <div className={classConcatMult(TextClass, EllipsedTextClass, props.className)}
        style={extractCss(props)} {...extractEventHandlers(props)}
        title={props.title} children={props.children} />;
}

EllipsedText.displayName = "EllipsedText";

Text.displayName = "Text";

export default Text;
