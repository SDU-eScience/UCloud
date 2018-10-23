import styled from "styled-components";
import { space, ButtonStyleProps, SpaceProps } from "styled-system";
import theme from "./theme";

const size = ({ size, theme }) => {
  switch (size) {
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

const fullWidth = (props) => (props.fullWidth ? { width: "100%" } : null)

const Button = styled.button<ButtonStyleProps & { fullWidth?: boolean } & SpaceProps>`
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
  background-color: ${props => props.theme.colors.blue};
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
  theme
};

Button.displayName = "Button";

export default Button;
