import * as React from "react";
import View from "Applications/View";
import { create } from "react-test-renderer";
import { mount, shallow } from "enzyme";
import { configure } from "enzyme";
import { detailedApplication } from "../mock/Applications";
import * as Adapter from "enzyme-adapter-react-16";
import { MemoryRouter } from "react-router";

configure({ adapter: new Adapter() });

describe("Detailed application", () => {
    test.skip("Mount component", () => {
        expect(create(
            <View match={{ params: { appName: "someName", appVersion: "someVersion" } }} />
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Set error message", () => {
        const error = "Error Message!";
        const detailedApp = mount(<View match={{ params: { appName: "someName", appVersion: "someVersion" } }} />);
        detailedApp.setState(() => ({ error }));
        expect(detailedApp.state("error")).toBe(error);
        expect(detailedApp.find("Message").props().content).toBe(error);
        detailedApp.find("Message").find("i").simulate("click");
        expect(detailedApp.find("Message").exists()).toBe(false);
    });

    test.skip("Component with application", () => {
        let detailedAppWrapper = mount(<MemoryRouter><View match={{ params: { appName: "someName", appVersion: "someVersion" } }} /></MemoryRouter>);
        detailedAppWrapper.find(View).instance().setState({ appInformation: detailedApplication, loading: false, complete: true });
        detailedAppWrapper = detailedAppWrapper.update();
        expect(detailedAppWrapper.html()).toMatchSnapshot();
    });
});