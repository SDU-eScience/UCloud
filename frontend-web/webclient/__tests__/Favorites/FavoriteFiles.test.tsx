import * as React from "react";
import FavoriteFiles from "../../app/Favorites/FavoriteFiles";
import { create } from "react-test-renderer";
import { createMemoryHistory } from "history";
import { configureStore } from "../../app/Utilities/ReduxUtilities";
import * as FavoritesRedux from "../../app/Favorites/Redux";
import { Provider } from "react-redux";
import { createResponsiveStateReducer, responsiveStoreEnhancer } from "redux-responsive";
import theme, { responsiveBP } from "../../app/ui-components/theme";
import { ThemeProvider } from "styled-components";

const store = configureStore({
    ...FavoritesRedux.init(),
}, {
    ...FavoritesRedux.reducers,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        { infinity: "xxl" })
}, responsiveStoreEnhancer)

describe("Favorite Files", () => {
    test.skip("Mount", () => {
        // The call to the backend doesn't fail, overwriting emptyPage with value 1
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <FavoriteFiles
                        header={null}
                        history={createMemoryHistory()}
                    />
                </ThemeProvider>
            </Provider>).toJSON()).toMatchSnapshot();
    });
});