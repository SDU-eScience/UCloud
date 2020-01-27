import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import {createMemoryHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import DetailedResult from "../../app/Applications/DetailedResult";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";
configure({adapter: new Adapter()});



describe("Detailed Result", () => {
    test("Mount DetailedResult", () => {
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <DetailedResult
                        history={createMemoryHistory()}
                        match={{
                            url: "", path: "", params: {jobId: "J0B1D"},
                            isExact: true
                        }}
                    />
                </ThemeProvider>
            </Provider>
        )).toMatchSnapshot();
    });
});
