import * as React from "react";
import DetailedResult from "Applications/DetailedResult";
import { createMemoryHistory } from "history";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initAnalyses } from "DefaultObjects";
import analyses from "Applications/Redux/AnalysesReducer";
import { Provider } from "react-redux";
import { configure, mount, shallow } from "enzyme";
import * as AnalysesActions from "Applications/Redux/ApplicationsActions";
import * as Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });



describe("Detailed Result", () => {
    // FIXME: contacts backend on creation
    test("Mount DetailedResult", () => {
        const store = configureStore({ analyses: initAnalyses() }, { analyses });
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
        let store = configureStore({ analyses: initAnalyses() }, { analyses });
        let wrapper = shallow(
            <Provider store={store}>
                <DetailedResult
                    match={{ params: { jobId: "J0B1D" } }}
                />
            </Provider>);
        console.warn(wrapper.find(DetailedResult).state());//.state("page"));//.instance().setState({ page: mockFiles_SensitivityConfidential, loading: false });
        wrapper.update();
        expect(wrapper.html()).toMatchSnapshot();
    });
});