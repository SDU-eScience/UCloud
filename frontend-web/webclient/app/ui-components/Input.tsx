import styled, {css} from "styled-components";
import {
    BorderProps, borderRadius, BorderRadiusProps,
    fontSize, FontSizeProps, space, SpaceProps,
    width, WidthProps
} from "styled-system";
import Text from "./Text";
import theme from "./theme";

export const borders = ({color, theme, noBorder}: {color?: string, theme?: any, noBorder?: boolean}) => {
    if (noBorder) return {"border-width": "0px"};
    const borderColor = color ? theme.colors[color] : theme.colors.borderGray;
    const focusColor = color ? borderColor : theme.colors.blue;
    return {
        "border-width": theme.borderWidth,
        "border-color": borderColor,
        "border-style": "solid",
        ":focus": {
            "outline": 0,
            "border-color": focusColor,
        }
    };
};

export interface InputProps extends BorderProps, SpaceProps, BorderRadiusProps, FontSizeProps, WidthProps {
    leftLabel?: boolean;
    rightLabel?: boolean;
    id?: string;
    color?: string;
    noBorder?: boolean;
    error?: boolean;
    showError?: boolean;
    autocomplete?: "on" | "off";
}

const left = ({leftLabel}: {leftLabel?: boolean}): string =>
    leftLabel ? `border-top-left-radius: 0; border-bottom-left-radius: 0;` : "";
const right = ({rightLabel}: {rightLabel?: boolean}): string =>
    rightLabel ? `border-top-right-radius: 0; border-bottom-right-radius: 0;` : "";

const Input = styled.input<InputProps>`
    display: block;
    font-family: inherit;
    background-color: ${props => props.error ? props.theme.colors.lightRed : "transparent"};
    ${fontSize}
    color: var(--black, #f00);

    margin: 0;

    ${p => p.showError ? `&:invalid {
        border-color: var(--red, #f00);
    }` : null};

    ::placeholder {
        color: var(--gray, #f00);
    }

    &:focus {
        outline: none;
        background-color: transparent;
    }

    ${borders} ${space} ${borderRadius}
    ${left} ${width} ${right}
`;

Input.displayName = "Input";

Input.defaultProps = {
    id: "default",
    width: "100%",
    noBorder: false,
    borderRadius: "5px",
    pt: "7px",
    pb: "7px",
    pl: "12px",
    pr: "12px",
};

export const HiddenInputField = styled(Input)`
  display: none;
`;

export default Input;

const rightLabel = ({rightLabel}: {rightLabel?: boolean}) => rightLabel ?
    css`border-top-right-radius: 5px; border-bottom-right-radius: 5px; border-left: 0px; margin-left: 0;` : null;
const leftLabel = ({leftLabel}: {leftLabel?: boolean}) => leftLabel ?
    css`border-top-left-radius: 5px; border-bottom-left-radius: 5px; border-right: 0px; margin-right: 0;` : null;
const independent = ({independent}: {independent?: boolean}) => independent ?
    css`border-radius: 5px; height: 42px;` : null;


export interface InputLabelProps extends WidthProps {
  leftLabel?: boolean;
  rightLabel?: boolean;
  independent?: boolean;
}

export const InputLabel = styled(Text) <InputLabelProps>`
  border: ${({theme}) => theme.colors.borderGray} solid ${theme.borderWidth};
  ${independent}
  ${leftLabel}
  ${rightLabel}
  ${width}
  padding-left: 1em;
  padding-right: 1em;
  padding-top: 6px;
`;
