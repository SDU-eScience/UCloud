import styled from "styled-components";
import {ButtonStyleProps, height, HeightProps, SizeProps, space, SpaceProps, width, WidthProps} from "styled-system";
import theme, {Theme, ThemeColor} from "./theme";

const size = (p: {size: string; theme: Theme}) => {
    switch (p.size) {
        case "tiny":
            return {
                fontSize: `${theme.fontSizes[0]}px`,
                padding: "5px 10px"
            };
        case "small":
            return {
                fontSize: `${theme.fontSizes[0]}px`,
                padding: "7px 12px"
            };
        case "medium":
            return {
                fontSize: `${theme.fontSizes[1]}px`,
                padding: "9.5px 18px"
            };
        case "large":
            return {
                fontSize: `${theme.fontSizes[2]}px`,
                padding: "12px 22px"
            };
        default:
            return {
                fontSize: `${theme.fontSizes[1]}px`,
                padding: "9.5px 18px"
            };
    }
};

export const fullWidth = (props: {fullWidth?: boolean}) => props.fullWidth ? {width: "100%"} : null;

export const attached = (props: {attached?: boolean}): string | null => props.attached ?
    `border-top-left-radius: 0;
    border-bottom-left-radius: 0;`
    : null;

export const asSquare = (props: {asSquare?: boolean}) => props.asSquare ? {
    borderRadius: 0
} : null;

export interface ButtonProps extends ButtonStyleProps, HeightProps, SpaceProps, SizeProps, WidthProps {
    fullWidth?: boolean;
    textColor?: ThemeColor;
    lineHeight?: number | string;
    title?: string;
    attached?: boolean;
    asSquare?: boolean;
}

const Button = styled.button<ButtonProps>`
  font-smoothing: antialiased;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  text-decoration: none;
  font-family: inherit;
  font-weight: ${theme.bold};
  line-height: ${props => props.lineHeight};
  cursor: pointer;
  border-radius: ${theme.radius};
  background-color: var(--${p => p.color}, #f00);
  color: var(--${p => p.textColor}, #f00);
  border-width: 0;
  border-style: solid;

  transition: ${theme.timingFunctions.easeInOut} ${theme.transitionDelays.small};

  &:disabled {
    opacity: 0.25;
  }

  &:focus {
    outline: none;
  }

  &:hover {
    transform: scale(1.03);
  }

  ${attached} ${asSquare} ${fullWidth} ${size} ${space} ${height} ${width};
`;

Button.defaultProps = {
    textColor: "white",
    color: "blue",
    lineHeight: 1.5
};

Button.displayName = "Button";

export default Button;
