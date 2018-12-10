import styled from "styled-components";
import { width, themeGet } from "styled-system";
import { borders } from "./Input";


export const TextArea = styled.textarea<{ width?: string | number }>`
    ${width}; ${borders};
    border-radius: 5px;
    border: 1px ${themeGet("colors.borderGray")};
    padding: 5px;
    resize: none;
    &:focus {
        outline: none;
    }
`

export default TextArea;