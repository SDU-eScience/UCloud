import * as React from "react";
import DetailedResult from "../../app/Applications/DetailedResult";
import {createMemoryHistory} from "history";
import {create} from "react-test-renderer";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {initAnalyses} from "../../app/DefaultObjects";
import analyses from "../../app/Applications/Redux/AnalysesReducer";
import {Provider} from "react-redux";
import {shallow} from "enzyme";
import "jest-styled-components";
// configure({ adapter: new Adapter() });



describe("Detailed Result", () => {
    // FIXME: contacts backend on creation
    test.skip("Mount DetailedResult", () => {
        const store = configureStore({analyses: initAnalyses()}, {analyses});
        expect(create(
            <Provider store={store}>
                <DetailedResult
                    history={createMemoryHistory()}
                    match={{
                        url: "", path: "", params: {jobId: "J0B1D"},
                        isExact: true
                    }}
                />
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

    test.skip("Render with files page", () => {
        const store = configureStore({analyses: initAnalyses()}, {analyses});
        let wrapper = shallow(
            <Provider store={store}>
                <DetailedResult
                    history={createMemoryHistory()}
                    match={{
                        isExact: true,
                        url: "",
                        path: "",
                        params: {jobId: "J0B1D"}
                    }}
                />
            </Provider>);
        wrapper.update();
        expect(wrapper.html()).toMatchSnapshot();
    });
});