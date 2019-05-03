import * as React from "react";
import Search from "../../app/Search/Search";
import { create } from "react-test-renderer";
import { createMemoryHistory } from "history";
import { MemoryRouter } from "react-router";
import { configureStore, responsive } from "../../app/Utilities/ReduxUtilities";
import files from "../../app/Files/Redux/FilesReducer";
import { initSimpleSearch, initFilesDetailedSearch, initApplicationsAdvancedSearch } from "../../app/DefaultObjects";
import theme from "../../app/ui-components/theme";
import { Provider } from "react-redux";
import simpleSearch from "../../app/Search/Redux/SearchReducer";
import detailedFileSearch from "../../app/Files/Redux/DetailedFileSearchReducer";
import detailedApplicationSearch from "../../app/Applications/Redux/DetailedApplicationSearchReducer";
import { ThemeProvider } from "styled-components";
import "jest-styled-components";


describe("Search", () => {
    test.skip("Mount", () => {
        expect(create(
            <Provider store={configureStore({
                simpleSearch: initSimpleSearch(),
                detailedFileSearch: initFilesDetailedSearch(),
                detailedApplicationSearch: initApplicationsAdvancedSearch()
            }, {
                    simpleSearch,
                    detailedFileSearch,
                    detailedApplicationSearch,
                    files,
                    responsive
                })}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <Search
                            match={{ params: { priority: "" }, isExact: false, path: "", url: "" }}
                            history={createMemoryHistory()}
                            location={{ search: "" }}
                        />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>)).toMatchSnapshot();
    });
})