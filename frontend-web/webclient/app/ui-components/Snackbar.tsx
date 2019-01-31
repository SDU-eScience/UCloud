import styled, { css } from "styled-components";
import { Box } from "ui-components";

// https://www.w3schools.com/howto/howto_js_snackbar.asp

const visibility = ({ visible }: { visible: boolean }) => visible ? css`
    visibility: visible;
    animation: fadein 0.5s, fadeout 0.5s 2.5s;
` : css`visibility: hidden;`;

export const /* Admiral */ Snackbar = styled(Box) <{ visible: boolean }>`
    min-width: ${props => props.minWidth};
    margin-left: calc(-${props => props.minWidth} / 2);
    background-color: ${props => props.theme.colors.black};
    color: ${props => props.theme.colors.white};
    text-align: center;
    border-radius: 2px;
    padding: 16px;
    position: fixed;
    z-index: 1;
    left: 50%;
    bottom: 30px;

    ${visibility}

    @-webkit-keyframes fadein {
        from { bottom: 0; opacity: 0; }
        to { bottom: 30px; opacity: 1; }
    }
    
    @keyframes fadein {
        from { bottom: 0; opacity: 0; }
        to { bottom: 30px; opacity: 1; }
    }
    
    @-webkit-keyframes fadeout {
        from { bottom: 30px; opacity: 1; }
        to { bottom: 0; opacity: 0; }
    }
    
    @keyframes fadeout {
        from { bottom: 30px; opacity: 1; }
        to { bottom: 0; opacity: 0; }
    }
`;