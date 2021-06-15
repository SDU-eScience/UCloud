import {Client} from "Authentication/HttpClientInstance";
import Core from "Core";
import Header from "Navigation/Header";
import {CONTEXT_SWITCH, USER_LOGIN, USER_LOGOUT} from "Navigation/Redux/HeaderReducer";
import * as React from "react";
import * as ReactDOM from "react-dom";
import {Provider} from "react-redux";
import {BrowserRouter} from "react-router-dom";
import {createGlobalStyle, ThemeProvider} from "styled-components";
import {theme, UIGlobalStyle} from "ui-components";
import {invertedColors} from "ui-components/theme";
import {findAvatar} from "UserSettings/Redux/AvataaarActions";
import {store} from "Utilities/ReduxUtilities";
import {isLightThemeStored, removeExpiredFileUploads, setSiteTheme, toggleCssColors} from "UtilityFunctions";
import {injectFonts} from "ui-components/GlobalStyle";

export function dispatchUserAction(type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH): void {
    store.dispatch({type});
}

export async function onLogin(): Promise<void> {
    const action = await findAvatar();
    if (action !== null) store.dispatch(action);
}

const GlobalStyle = createGlobalStyle`
  ${UIGlobalStyle}
`;

Client.initializeStore(store);
removeExpiredFileUploads();

function App({children}: {children?: React.ReactNode}): JSX.Element {
    const [isLightTheme, setTheme] = React.useState(() => {
        const isLight = isLightThemeStored();
        toggleCssColors(isLight);
        return isLight;
    });
    const setAndStoreTheme = (isLight: boolean): void => (setSiteTheme(isLight), setTheme(isLight));

    function toggle(): void {
        toggleCssColors(isLightTheme);
        setAndStoreTheme(!isLightTheme);
    }

    return (
        <ThemeProvider theme={isLightTheme ? theme : {...theme, colors: invertedColors}}>
            <GlobalStyle />
            <BrowserRouter basename="app">
                <Header toggleTheme={toggle} />
                {children}
            </BrowserRouter>
        </ThemeProvider>
    );
}

injectFonts();

ReactDOM.render(
    (
        <Provider store={store}>
            <App>
                <Core />
            </App>
        </Provider>
    ),
    document.getElementById("app")
);
