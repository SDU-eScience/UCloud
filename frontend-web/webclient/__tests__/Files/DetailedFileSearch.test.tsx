import * as React from "react";
import DetailedFileSearch from "../../app/Files/DetailedFileSearch";
import {Provider} from "react-redux";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {initFilesDetailedSearch} from "../../app/DefaultObjects";
import detailedFileSearch from "../../app/Files/Redux/DetailedFileSearchReducer";
import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";
import {ThemeProvider} from "styled-components";
import theme from "../../app/ui-components/theme";
import {create} from "react-test-renderer";
import {MemoryRouter} from "react-router";

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