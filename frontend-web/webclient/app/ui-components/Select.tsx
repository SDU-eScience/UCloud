import * as React from "react";
import styled from "styled-components";
import {fontSize, space, SpaceProps} from "styled-system";
import Flex from "./Flex";
import Icon from "./Icon";
import theme from "./theme";

const ClickableIcon = styled(Icon)`
  pointer-events: none;
`;

const left = ({leftLabel}: {leftLabel?: boolean}) =>
  leftLabel ? `border-top-left-radius: 0; border-bottom-left-radius: 0;` : "";
const right = ({rightLabel}: {rightLabel?: boolean}) =>
  rightLabel ? `border-top-right-radius: 0; border-bottom-right-radius: 0;` : "";


const SelectBase = styled.select<{
  fontSize?: number | string,
  leftLabel?: boolean,
  rightLabel?: boolean,
  showError?: boolean
} & SpaceProps>`
  appearance: none;
  display: block;
  width: 100%;
  font-family: inherit;
  color: inherit;

  ${p => p.showError ? `&:invalid {
    border-color: ${p.theme.colors.red};
  }` : null}

  background-color: transparent;
  border-radius: ${theme.radius};
  border-width: ${({theme}) => theme.borderWidth};
  border-style: solid;
  border-color: ${p => p.theme.colors.borderGray};

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.blue};
  }
  
  ${space} ${fontSize} 
  ${left} ${right}
`;

SelectBase.defaultProps = {
  theme,
  m: 0,
  pl: 12,
  pr: 32,
  py: 7
};

const Select = styled((props: any) => (
  <Flex width={1} alignItems="center">
    <SelectBase {...props} ref={props.selectRef} />
    <ClickableIcon ml={-32} name="chevronDown" color="gray" size="0.7em" />
  </Flex>
))``;

export default Select;
