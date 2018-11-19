import * as React from 'react'
import styled from 'styled-components'
import { color } from 'styled-system'
import theme from './theme'
import Icon from './Icon'
import Text from './Text'

const Radio = props => {
  const { checked, disabled } = props

  const radioIconName = checked ? 'radioChecked' : 'radioEmpty'

  return (
    <RadioWrap checked={checked} disabled={disabled}>
      <RadioInput type="radio" {...props} />
      <RadioIcon name={radioIconName} size={24} />
    </RadioWrap>
  )
}

interface RadioWrapProps {
  checked: boolean
  disabled: boolean
}

const RadioWrap = styled("div")<RadioWrapProps>`
  display: inline-block;
  color: ${props => props.theme.colors.borderGray};
  &:hover {
    ${props =>
      props.checked || props.disabled
        ? null
        : `color: ${props.theme.colors.blue};`};
  }
`;

const RadioInput = styled.input`
  appearance: none;
  opacity: 0;
  position: absolute;
  z-index: 0;
  &:focus {
    box-shadow: none;
  }
  &:checked ~ svg {
    color: ${props => props.theme.colors.blue};
  }
  &:disabled ~ svg {
    color: ${props => props.theme.colors.borderGray};
  }
`;

const RadioIcon = styled(Icon)`
  vertical-align: middle;
`;

Radio.defaultProps = {
  theme: theme
};

export default Radio;
