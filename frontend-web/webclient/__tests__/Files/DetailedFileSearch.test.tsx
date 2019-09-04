import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {initFilesDetailedSearch} from "../../app/DefaultObjects";
import DetailedFileSearch from "../../app/Files/DetailedFileSearch";
import detailedFileSearch from "../../app/Files/Redux/DetailedFileSearchReducer";
import theme from "../../app/ui-components/theme";
import {configureStore} from "../../app/Utilities/ReduxUtilities";

configure({adapter: new Adapter()});

const store = configureStore({detailedFileSearch: initFilesDetailedSearch()}, {detailedFileSearch});

describe("Detailed File Search", () => {
    it("Mount file search", () => {
        expect(create(<Provider store={store}>
            <ThemeProvider theme={theme}>
                <MemoryRouter>
                    <DetailedFileSearch

                    />
                </MemoryRouter>
            </ThemeProvider>
        </Provider>).toJSON()).toMatchSnapshot()
    });
})