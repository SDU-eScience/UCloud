import styled from "styled-components";
import Button from "./Button";
import { ButtonStyleProps } from "styled-system";
import theme from "./theme";

export interface OutlineButtonProps extends ButtonStyleProps { hoverColor?: string }

// FIXME Have color return text color (color) and outline color (border 3rd arg) as two different things

const OutlineButton = styled(Button) <OutlineButtonProps>`
  color: ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  border: 1px solid ${props => props.color ? props.theme.colors[props.color] : props.theme.colors.blue};
  border-radius: ${props => props.theme.radius};
  background-color: transparent;

  &:hover {
    color: ${props => (props.disabled ? null : (props.hoverColor ? props.theme.colors[props.hoverColor] : null))};
    border: 1px solid ${props => props.hoverColor ? props.theme.colors[props.hoverColor] : null};
    background-color: transparent;
    transition: ease 0.1s;
  }
`;

OutlineButton.defaultProps = {
  theme
};

OutlineButton.displayName = "OutlineButton";

export default OutlineButton;
