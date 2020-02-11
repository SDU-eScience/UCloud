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
import {isLightThemeStored, setSiteTheme, toggleCssColors} from "UtilityFunctions";

export function dispatchUserAction(type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH): void {
    store.dispatch({type});
}

export async function onLogin(): Promise<void> {
    const action = await findAvatar();
    if (action !== null) store.dispatch(action);
}

const GlobalStyle = createGlobalStyle`
  ${() => UIGlobalStyle}
`;

Client.initializeStore(store);

function App({children}: React.PropsWithChildren<{}>): JSX.Element {
    const [isLightTheme, setTheme] = React.useState(() => {
        const isLight = isLightThemeStored();
        if (!isLight) toggleCssColors(isLight);
        return isLight;
    });
    const setAndStoreTheme = (isLight: boolean): void => (setSiteTheme(isLight), setTheme(isLight));

    function toggle(): void {
        toggleCssColors(isLightTheme);
        setAndStoreTheme(!isLightTheme);
    }

    return (
        <ThemeProvider theme={isLightTheme ? theme : {...theme, colors: invertedColors}}>
            <>
                <GlobalStyle />
                <BrowserRouter basename="app">
                    <Header toggleTheme={toggle} />
                    {children}
                </BrowserRouter>
            </>
        </ThemeProvider>
    );
}

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
