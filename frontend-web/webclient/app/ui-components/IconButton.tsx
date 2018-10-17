import * as React from 'react'
import styled from 'styled-components'
import Icon from './Icon'
import Button, { ButtonProps } from './Button'
import theme from './theme'

export interface IconbuttonProps extends ButtonProps {

}

const TransparentButton = styled<IconbuttonProps, IconbuttonProps>(Button)`
  padding: 0;
  height: auto;
  background-color: transparent;
  color: inherit;

  &:hover {
    background-color: transparent;
  }
`;

const IconButton = ({ name, size, legacy, color, ...props }: IconbuttonProps) => (
  <TransparentButton {...props}>
    <Icon name={name} size={size} color={color} />
  </TransparentButton>
);

IconButton.displayName = 'IconButton';

/*
IconButton.propTypes = {
  onClick: PropTypes.func,
  title: PropTypes.string
};
*/

IconButton.defaultProps = {
  theme: theme
}

export default IconButton;
