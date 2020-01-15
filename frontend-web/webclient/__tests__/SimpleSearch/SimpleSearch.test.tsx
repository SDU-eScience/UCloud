import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import {createBrowserHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Search from "../../app/Search/Search";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

configure({adapter: new Adapter()});

test("Search mount", () => {
    expect(
        create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <Search
                            match={{
                                isExact: false,
                                params: {priority: "FILES"},
                                url: "",
                                path: ""
                            }}
                            history={createBrowserHistory()}
                            location={{search: ""}}
                        />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>
        )
    ).toMatchSnapshot();
});
