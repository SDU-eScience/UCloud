import styled from "styled-components";
import {borderColor, space} from "styled-system";
import Box from "./Box";
import Input from "./Input";
import theme from "./theme";

export type InputGroupProps = any;

const InputGroup = styled.div<InputGroupProps>`
  display: flex;
  align-items: center;
  border-radius: ${theme["radius"]};
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
  borderColor: "borderGray"
};

InputGroup.displayName = "InputGroup";

export default InputGroup;
