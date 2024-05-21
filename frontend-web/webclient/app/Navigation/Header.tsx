import * as React from "react";
import * as ui from "@/ui-components";
import {injectStyleSimple} from "@/Unstyled";
import {Link} from "react-router-dom";
import AppRoutes from "@/Routes";

// Note(Jonas): We need something similar NOT in the header. Or maybe keep this? Nah.
export function NonAuthenticatedHeader(): React.ReactNode {
    return (
        <ui.Box background={"var(--sidebarColor)"} className={HeaderContainerClass}>
            <Link to={AppRoutes.login.login()} title="Login">
                <ui.Icon name="logoEsc" mt="10px" size="34px" />
            </Link>
        </ui.Box>
    );
}

const HeaderContainerClass = injectStyleSimple("header-container", `
    height: 100vh;
    position: fixed;
    cursor: pointer;
    align-items: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    top: 0;
    width: var(--sidebarWidth);
    z-index: 100;
`);
