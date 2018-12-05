import styled from "styled-components";
import { width, themeGet } from "styled-system";


export const TextArea = styled.textarea<{ width?: string | number }>`
    ${width};
    border-radius: 5px;
    border: 1px solid ${themeGet("colors.borderGray")};
    padding: 5px;
    resize: none;
    &:focus {
        outline: none;
    }
`

export default TextArea;