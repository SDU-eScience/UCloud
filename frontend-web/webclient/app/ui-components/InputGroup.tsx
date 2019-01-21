import styled from 'styled-components';
import { space, borderColor } from 'styled-system';
import theme from './theme';
import Box from './Box';
import Input from './Input';

export type InputGroupProps = any;

const InputGroup = styled.div<InputGroupProps>`
  display: flex;
  align-items: center;
  border-radius: ${theme['radius']};
  border-width: 1px;
  border-style: solid;
  ${borderColor}
  ${space}

  & > ${Box} {
    width: 100%;
    flex: 1 1 auto;
  }

  & ${Input} {
    border: 0;
    box-shadow: none;
  }
`;

InputGroup.defaultProps = {
  theme,
  borderColor: 'borderGray'
};

export default InputGroup;
