import styled from 'styled-components'
import { space, themeGet, BorderProps, SpaceProps, ColorValue, lineHeight } from 'styled-system'
import defaultTheme from './theme'

const borders = ({ color, theme, noBorder }: { color?: ColorValue, theme?: any, noBorder?: boolean }) => {
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

export interface InputProps extends BorderProps, SpaceProps {
  id?: string
  color?: string
  noBorder?: boolean
}

const Input = styled("input") <InputProps>`
  appearance: none;
  display: block;
  width: 100%;
  font-family: inherit;
  color: inherit;
  font-size: ${themeGet("fontSizes.1")}px;
  background-color: transparent;
  border-radius: ${themeGet("radius")};
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

  ${borders} ${space};
`;

Input.displayName = "Input";

Input.defaultProps = {
  id: "default",
  theme: defaultTheme,
  noBorder: false
};

export const HiddenInputField = styled(Input)`
  display: none;
`;

export default Input;
