import styled from "styled-components";
import { space, ButtonStyleProps, SpaceProps, SizeProps } from "styled-system";
import theme from "./theme";

const size = ({ size, theme }: { size: string, theme: any }) => {
  switch (size) {
    case "tiny":
      return {
        fontSize: `${theme.fontSizes[0]}px`,
        padding: "6.5px 12px"
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

export type ButtonProps = ButtonStyleProps & { fullWidth?: boolean } & SpaceProps & SizeProps & { title?: string }

const Button = styled.button<ButtonProps>` 
  -webkit-font-smoothing: antialiased;
  display: inline-block;
  vertical-align: middle;
  text-align: center;
  text-decoration: none;
  font-family: inherit;
  font-weight: 600;
  line-height: 1.5;
  cursor: pointer;
  border-radius: ${props => props.theme.radius};
  background-color: ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  color: ${props => props.theme.colors.white};
  border-width: 0;
  border-style: solid;

  &:disabled {
    opacity: 0.25;
  }

  &:hover {
    transition: ease 0.3s;
    background-color: ${props =>
    props.disabled ? null : props.theme.colors.darkBlue};
  }

  ${fullWidth} ${size} ${space};
`;

Button.defaultProps = {
  theme,
  color: "blue"
};

Button.displayName = "Button";

export default Button;
