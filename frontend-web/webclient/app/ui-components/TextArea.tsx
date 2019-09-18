import styled from "styled-components";
import {width} from "styled-system";
import {borders} from "./Input";


export const TextArea = styled.textarea<{width?: string | number}>`
    ${width}; ${borders};
    border-radius: 5px;
    border: ${({theme}) => theme.borderWidth} solid ${({theme}) => theme.colors.borderGray};
    background-color: ${({theme}) => theme.colors.white};
    color: ${({theme}) => theme.colors.black};
    padding: 5px;
    resize: none;
    vertical-align: top;
    &:focus {
        outline: none;
    }
`;

TextArea.displayName = "TextArea";

export default TextArea;
