import * as React from "react";
import {extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import {CSSProperties} from "react";
import {BoxProps} from "@/ui-components/Box";

export interface TextProps extends BoxProps {
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

const Text: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <div className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
                title={props.title} children={props.children}/>;
};

export const TextDiv = Text;
export const TextSpan: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <span className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
                 title={props.title} children={props.children}/>;
}
export const TextP: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <p className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

export const TextH1: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <h1 className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

export const TextH2: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <h2 className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

export const TextH3: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <h3 className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

export const TextH4: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <h4 className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

export const TextH5: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <h5 className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

export const TextH6: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <h6 className={TextClass + " " + (props.className ?? "")} style={extractCss(props)} {...extractEventHandlers(props)}
              title={props.title} children={props.children}/>;
}

const EllipsedTextClass = injectStyle("ellipsed-text", k => `
    ${k} {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        display: inline-block;
        vertical-align: bottom;   
    }
`);

export const EllipsedText: React.FunctionComponent<TextProps & { children?: React.ReactNode }> = props => {
    return <div className={TextClass + " " + EllipsedTextClass}
                style={extractCss(props)} {...extractEventHandlers(props)}
                title={props.title} children={props.children}/>;
}

EllipsedText.displayName = "EllipsedText";

Text.defaultProps = {
    cursor: "inherit"
};

Text.displayName = "Text";

export default Text;
