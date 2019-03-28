import styled from 'styled-components'
import {
  space, themeGet, BorderProps, SpaceProps,
  BorderRadiusProps, borderRadius,
  fontSize, FontSizeProps
} from 'styled-system'
import defaultTheme from './theme'
import Text from './Text';

export const borders = ({ color, theme, noBorder }: { color?: string, theme?: any, noBorder?: boolean }) => {
  if (noBorder) return "";
  const borderColor = color ? theme.colors[color] : theme.colors.borderGray
  const focusColor = color ? borderColor : theme.colors.blue
  return {
    'border-color': borderColor,
    'box-shadow': `0 0 0 1px ${borderColor}`,
    ':focus': {
      outline: 0,
      'border-color': focusColor,
      'box-shadow': `0 0 0 2px ${focusColor}`
    }
  }
}

export interface InputProps extends BorderProps, SpaceProps, BorderRadiusProps,
  FontSizeProps {
  leftLabel?: boolean
  rightLabel?: boolean
  id?: string
  color?: string
  noBorder?: boolean
  error?: boolean
}

const left = ({ leftLabel }: { leftLabel?: boolean }) => leftLabel ? `border-top-left-radius: 0; border-bottom-left-radius: 0;` : "";
const right = ({ rightLabel }: { rightLabel?: boolean }) => rightLabel ? `border-top-right-radius: 0; border-bottom-right-radius: 0;` : "";

const Input = styled.input<InputProps>`
  display: block;
  width: 100%;
  font-family: inherit;
  color: ${props => props.error ? "red" : "inherit"};
  ${fontSize}
  background-color: transparent;
  border-width: 0px;
  border-style: solid;
  border-color: ${themeGet("colors.borderGray")};

  padding-top: 7px;
  padding-bottom: 7px;
  padding-left: 12px;
  padding-right: 12px;

  margin: 0;

  ::placeholder {
    color: ${themeGet("colors.gray")};
  }

  ::-ms-clear {
    display: none;
  }

  &:focus {
    outline: none;
  }

  ${borders} ${space} ${borderRadius}
  ${left}
  ${right}
`;

Input.displayName = "Input";

Input.defaultProps = {
  id: "default",
  theme: defaultTheme,
  noBorder: false,
  borderRadius: "5px",
};

export const HiddenInputField = styled(Input)`
  display: none;
`;

export default Input;

const rightLabel = ({ rightLabel }: { rightLabel?: boolean }) => rightLabel ? `border-top-right-radius: 5px; border-bottom-right-radius: 5px; border-left: 0px;` : null;
const leftLabel = ({ leftLabel }: { leftLabel?: boolean }) => leftLabel ? `border-top-left-radius: 5px; border-bottom-left-radius: 5px; border-right: 0px;` : null;

export const InputLabel = styled(Text) <{ leftLabel?: boolean, rightLabel?: boolean }>`
  border: ${themeGet("colors.borderGray")} solid 1px;
  margin: -1px;
  ${leftLabel}
  ${rightLabel}
  padding-left: 1%;
  padding-right: 1%;
  padding-top: 6px;
`