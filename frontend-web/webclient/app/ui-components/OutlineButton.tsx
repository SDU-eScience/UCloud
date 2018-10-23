import styled from "styled-components";
import Button from "./Button";
import { ButtonStyleProps } from "styled-system";
import theme from "./theme";

export interface OutlineButtonProps extends ButtonStyleProps { }

// FIXME Have color return text color (color) and outline color (border 3rd arg) as two different things

const OutlineButton = styled(Button) <OutlineButtonProps>`
  color: ${props => props.color ? props.color : props.theme.colors.blue};
  border: 1px solid ${props => props.color ? props.color : props.theme.colors.blue};
  border-radius: 3px;
  background-color: transparent;

  &:hover {
    color: ${props => (props.disabled ? null : (props.color ? props.color : props.theme.colors.darkBlue))};
    border: 1px solid ${props => props.color ? props.color : props.theme.colors.blue};
    border-radius: 3px;
    background-color: transparent;
    transition: ease 0.1s;
  }
`;

OutlineButton.defaultProps = {
  theme
};

OutlineButton.displayName = "OutlineButton";

export default OutlineButton;
