import * as React from "react";
import DetailedResult from "Applications/DetailedResult";
import { createMemoryHistory } from "history";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initAnalyses, initDetailedResult } from "DefaultObjects";
import analyses from "Applications/Redux/AnalysesReducer";
import { Provider } from "react-redux";
import { configure, shallow } from "enzyme";
import detailedResult from "Applications/Redux/DetailedResultReducer";
import * as Adapter from "enzyme-adapter-react-16";

// configure({ adapter: new Adapter() });



describe("Detailed Result", () => {
    // FIXME: contacts backend on creation
    test.skip("Mount DetailedResult", () => {
        const store = configureStore({ analyses: initAnalyses(), detailedResult: initDetailedResult() }, { analyses, detailedResult });
        expect(create(
            <Provider store={store}>
                <DetailedResult
                    history={createMemoryHistory()}
                    match={{
                        url: "", path: "", params: { jobId: "J0B1D" },
                        isExact: true
                    }}
                />
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

    test.skip("Render with files page", () => {
        const store = configureStore({ analyses: initAnalyses(), detailedResult: initDetailedResult() }, { analyses, detailedResult });
        let wrapper = shallow(
            <Provider store={store}>
                <DetailedResult
                    match={{ jobId: "J0B1D" }}
                />
            </Provider>);
        wrapper.update();
        expect(wrapper.html()).toMatchSnapshot();
    });
});