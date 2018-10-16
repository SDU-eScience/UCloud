import styled from 'styled-components'
import { color } from 'styled-system'
import Button from './Button'
import theme from './theme'
import { ButtonProps } from 'semantic-ui-react';

export interface OutlineButtonProps extends ButtonProps {

}

const OutlineButton = styled<OutlineButtonProps, OutlineButtonProps>(Button)`
  color: ${props => props.theme.colors.blue};
  box-shadow: inset 0 0 0 2px ${props => props.theme.colors.blue};
  background-color: transparent;

  &:hover {
    color: ${props => (props.disabled ? null : props.theme.colors.darkBlue)};
    box-shadow: inset 0 0 0 2px
      ${props => (props.disabled ? null : props.theme.colors.darkBlue)};
    background-color: transparent;
  }
`;

OutlineButton.defaultProps = {
  theme: theme
};

OutlineButton.displayName = 'OutlineButton';

export default OutlineButton;
