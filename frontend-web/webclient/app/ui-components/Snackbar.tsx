import {injectStyle} from "@/Unstyled";
import * as React from "react";

// https://www.w3schools.com/howto/howto_js_snackbar.asp
const SnackbarClass = injectStyle("snackbar", k => `
    ${k} {
        min-width: 250px;
        width: auto;
        background-color: var(--textPrimary, #f00);
        color: var(--backgroundDefault, #f00);
        text-align: center;
        border-radius: 6px;
        padding: 16px 32px 8px 24px;
        display: flex;
        flex-direction: column;
        justify-content: center;
        position: fixed;
        z-index: 200;
        left: 50%;
        transform: translate(-50%);
        bottom: 30px;
        user-select: none;
        -webkit-user-select: none;
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

export function /* Admiral */ Snackbar({children}: React.PropsWithChildren) {
    return <div className={SnackbarClass}>{children}</div>;
}

Snackbar.displayName = "Snackbar";
