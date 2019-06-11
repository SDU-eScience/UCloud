import styled from "styled-components";
import { space, ButtonStyleProps, SpaceProps, SizeProps, height, HeightProps } from "styled-system";
import theme, { Theme, ThemeColor } from "./theme";

const size = ({ size, theme }: { size: string, theme: Theme }) => {
  switch (size) {
    case "tiny":
      return {
        fontSize: `${theme.fontSizes[0]}px`,
        padding: "5px 10px"
      }
    case "small":
      return {
        fontSize: `${theme.fontSizes[0]}px`,
        padding: "7px 12px"
      }
    case "medium":
      return {
        fontSize: `${theme.fontSizes[1]}px`,
        padding: "9.5px 18px"
      }
    case "large":
      return {
        fontSize: `${theme.fontSizes[2]}px`,
        padding: "12px 22px"
      }
    default:
      return {
        fontSize: `${theme.fontSizes[1]}px`,
        padding: "9.5px 18px"
      }
  }
};

export const fullWidth = (props: { fullWidth?: boolean }) => (props.fullWidth ? { width: "100%" } : null)

export type ButtonProps = ButtonStyleProps & HeightProps & { fullWidth?: boolean, textColor?: ThemeColor, lineHeight?: number | string } & SpaceProps & SizeProps & { title?: string }

const Button = styled.button<ButtonProps>` 
  -webkit-font-smoothing: antialiased;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  text-decoration: none;
  font-family: inherit;
  font-weight: ${props => props.theme.bold};
  line-height: ${props => props.lineHeight};
  cursor: pointer;
  border-radius: ${props => props.theme.radius};
  background-color: ${props => props.theme.colors[props.color!]};
  color: ${props => props.theme.colors[props.textColor!]};
  border-width: 0;
  border-style: solid;
  
  transition: ${({ theme }) => theme.timingFunctions.easeInOut} ${({ theme }) => theme.transitionDelays.small};

  &:disabled {
    opacity: 0.25;
  }

  &:focus {
    outline: none;
  }

  &:hover { 
    transform: scale(1.03);
  }

  ${fullWidth} ${size} ${space} ${height};
`;

Button.defaultProps = {
  theme,
  textColor: "white",
  color: "blue",
  lineHeight: 1.5
};

Button.displayName = "Button";

export default Button;
