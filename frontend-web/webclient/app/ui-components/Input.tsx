import styled, {css} from "styled-components";
import {
  BorderProps, borderRadius, BorderRadiusProps,
  fontSize, FontSizeProps, space, SpaceProps,
  width, WidthProps
} from "styled-system";
import Text from "./Text";
import defaultTheme from "./theme";

export const borders = ({color, theme, noBorder}: {color?: string, theme?: any, noBorder?: boolean}) => {
  if (noBorder) return "";
  const borderColor = color ? theme.colors[color] : theme.colors.borderGray;
  const focusColor = color ? borderColor : theme.colors.blue;
  return {
    "border-color": borderColor,
    "box-shadow": `0 0 0 1px ${borderColor}`,
    ":focus": {
      "outline": 0,
      "border-color": focusColor,
      "box-shadow": `0 0 0 1px ${focusColor},inset 0 0 0 1px ${focusColor}`
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

const left = ({leftLabel}: {leftLabel?: boolean}) => leftLabel ? `border-top-left-radius: 0; border-bottom-left-radius: 0;` : "";
const right = ({rightLabel}: {rightLabel?: boolean}) => rightLabel ? `border-top-right-radius: 0; border-bottom-right-radius: 0;` : "";

const Input = styled.input<InputProps>`
  display: block;
  font-family: inherit;
  color: ${props => props.error ? "red" : "inherit"};
  ${fontSize}
  background-color: transparent;
  border-width: 0px;
  border-style: solid;
  border-color: ${({theme}) => theme.colors.borderGray};

  padding-top: 7px;
  padding-bottom: 7px;
  padding-left: 12px;
  padding-right: 12px;

  margin: 0;

  ${({showError, theme}) => showError ? `&:invalid {
    background-color: ${theme.colors.lightRed};
  }` : null};

  ::placeholder {
    color: ${({theme}) => theme.colors.gray};
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
  theme: defaultTheme,
  width: "100%",
  noBorder: false,
  borderRadius: "5px",
};

export const HiddenInputField = styled(Input)`
  display: none;
`;

export default Input;

const rightLabel = ({rightLabel}: {rightLabel?: boolean}) => rightLabel ?
  css`border-top-right-radius: 5px; border-bottom-right-radius: 5px; border-left: 0px; margin-left: 0;` : null;
const leftLabel = ({leftLabel}: {leftLabel?: boolean}) => leftLabel ?
  css`border-top-left-radius: 5px; border-bottom-left-radius: 5px; border-right: 0px; margin-right: 0;` : null;

export interface InputLabelProps extends WidthProps {
  leftLabel?: boolean;
  rightLabel?: boolean;
}

export const InputLabel = styled(Text) <InputLabelProps>`
  border: ${({theme}) => theme.colors.borderGray} solid 1px;
  margin: -1px;
  ${leftLabel}
  ${rightLabel}
  ${width}
  padding-left: 1em;
  padding-right: 1em;
  padding-top: 6px;
`;
