import styled, { css, keyframes } from "styled-components";
import Box from "./Box";

// https://www.w3schools.com/howto/howto_js_snackbar.asp

const visibility = ({ visible }: { visible: boolean }) => visible ? css`
    visibility: visible;
    animation: ${fadeIn} 0.5s;
` : css`visibility: hidden;`;

const fadeIn = keyframes`
    from {
        bottom: 0; opacity: 0; 
    }
    to { 
        bottom: 30px; opacity: 1; 
    }
`;

export const /* Admiral */ Snackbar = styled(Box) <{ visible: boolean }>`
    min-width: 250px;
    width: auto;
    background-color: ${props => props.theme.colors.black};
    color: ${props => props.theme.colors.white};
    text-align: center;
    border-radius: 2px;
    padding: 16px;
    position: fixed;
    z-index: 1;
    left: 50%;
    transform: translate(-50%);
    bottom: 30px;

    ${visibility}
`;