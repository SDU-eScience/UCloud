import styled from "styled-components";
import Button from "./Button";
import { ButtonStyleProps } from "styled-system";
import theme, { Theme }from "./theme";

export interface OutlineButtonProps extends ButtonStyleProps { hoverColor?: string }

//Different from the one in button because of border size
const size = ({ size, theme }: { size: string, theme: Theme }) => {
  switch (size) {
    case "tiny":
      return {
        fontSize: `${theme.fontSizes[0]}px`,
        padding: "3px 10px"
      }
    case "small":
      return {
        fontSize: `${theme.fontSizes[0]}px`,
        padding: "5px 12px"
      }
    case "medium":
      return {
        fontSize: `${theme.fontSizes[1]}px`,
        padding: "7.5px 18px"
      }
    case "large":
      return {
        fontSize: `${theme.fontSizes[2]}px`,
        padding: "10px 22px"
      }
    default:
      return {
        fontSize: `${theme.fontSizes[1]}px`,
        padding: "7.5px 18px"
      }
  }
};

// FIXME Have color return text color (color) and outline color (border 3rd arg) as two different things

const OutlineButton = styled(Button) <OutlineButtonProps>`
  color: ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  border: 2px solid ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  border-radius: ${props => props.theme.radius};
  background-color: transparent;

  &:hover {
    color: ${props => (props.disabled ? null : (props.hoverColor ? props.theme.colors[props.hoverColor] : null))};
    border: 2px solid ${props => props.hoverColor ? props.theme.colors[props.hoverColor] : null};
    background-color: transparent;
    transition: ease 0.1s;
  }

  ${size}
`;

OutlineButton.defaultProps = {
  theme
};

OutlineButton.displayName = "OutlineButton";

export default OutlineButton;
