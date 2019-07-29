import * as React from 'react';
import styled from 'styled-components';
import {space, fontSize, SpaceProps } from 'styled-system';

import theme from './theme';
import Flex from './Flex';
import Icon from './Icon';

const ClickableIcon = styled(Icon)`
  pointer-events: none;
`;

const left = ({ leftLabel }: { leftLabel?: boolean }) => leftLabel ? `border-top-left-radius: 0; border-bottom-left-radius: 0;` : "";
const right = ({ rightLabel }: { rightLabel?: boolean }) => rightLabel ? `border-top-right-radius: 0; border-bottom-right-radius: 0;` : "";


const SelectBase = styled.select<{ fontSize?: number | string, leftLabel?: boolean, rightLabel?: boolean, showError?: boolean } & SpaceProps>`
  appearance: none;
  display: block;
  width: 100%;
  font-family: inherit;
  color: inherit;

  ${({showError, theme}) => showError ? `&:invalid {
    background-color: ${theme.colors.lightRed};
  }` : null}

  background-color: transparent;
  border-radius: ${theme.radius};
  border-width: 1px;
  border-style: solid;
  border-color: ${props => props.theme.colors.borderGray};
  ${space} ${fontSize} &:focus {
    outline: none;
    border-color: ${props => props.theme.colors.blue};
    box-shadow: 0 0 0 1px ${props => props.theme.color.blue};
  }
  ${left}
  ${right}
`;

SelectBase.defaultProps = {
  theme,
  fontSize: 1,
  m: 0,
  pl: 12,
  pr: 32,
  py: 6.5
};

const Select = styled((props: any) => (
  <Flex width={1} alignItems="center">
    <SelectBase {...props} ref={props.selectRef} />
    <ClickableIcon ml={-32} name="chevronDown" color="gray" size="0.7em" />
  </Flex>
))``;

export default Select;