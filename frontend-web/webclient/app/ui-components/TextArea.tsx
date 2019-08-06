import styled from "styled-components";
import {width} from "styled-system";
import {borders} from "./Input";


export const TextArea = styled.textarea<{width?: string | number}>`
    ${width}; ${borders};
    border-radius: 5px;
    border: 1px ${({theme}) => theme.colors.borderGray};
    padding: 5px;
    resize: none;
    &:focus {
        outline: none;
    }
`;

TextArea.displayName = "TextArea";

export default TextArea;