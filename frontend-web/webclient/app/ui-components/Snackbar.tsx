import {injectStyle} from "@/Unstyled";
import * as React from "react";

// https://www.w3schools.com/howto/howto_js_snackbar.asp
const SnackbarClass = injectStyle("snackbar", k => `
    ${k} {
        min-width: 250px;
        width: auto;
        background-color: var(--black, #f00);
        color: var(--white, #f00);
        text-align: center;
        border-radius: 2px;
        padding: 16px;
        position: fixed;
        z-index: 200;
        left: 50%;
        transform: translate(-50%);
        bottom: 30px;
        user-select: none;
        visibility: visible;
        animation: snackbar-fade 0.5s;
    }
    
    @keyframes snackbar-fade {
        from {
            bottom: 0; opacity: 0;
        }
        to {
            bottom: 30px; opacity: 1;
        }   
    }
`);

export const /* Admiral */ Snackbar: React.FunctionComponent<{ children?: React.ReactNode; }> = props => {
    return <div className={SnackbarClass}>{props.children}</div>;
};

Snackbar.displayName = "Snackbar";
