import styled from 'styled-components'
import { space, themeGet, BorderProps, SpaceProps, 
         BorderRadiusProps, borderRadius, 
         fontSize, FontSizeProps } from 'styled-system'
import defaultTheme from './theme'

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
                                    FontSizeProps 
{
  id?: string
  color?: string
  noBorder?: boolean
  error?: boolean
}

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
