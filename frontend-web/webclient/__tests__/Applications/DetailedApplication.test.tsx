import * as React from "react";
import DetailedApplication from "Applications/DetailedApplication";
import { create } from "react-test-renderer";
import { mount, shallow } from "enzyme";
import { Message } from "semantic-ui-react";
import { configure } from "enzyme";
import { detailedApplication } from "../mock/Applications";
import * as Adapter from "enzyme-adapter-react-16";
import { MemoryRouter } from "react-router";

configure({ adapter: new Adapter() });

describe("Detailed application", () => {
    test("Mount component", () => {
        expect(create(
            <DetailedApplication match={{ params: { appName: "someName", appVersion: "someVersion" } }} />
        ).toJSON()).toMatchSnapshot();
    });

    test("Set error message", () => {
        const error = "Error Message!";
        const detailedApp = mount(<DetailedApplication match={{ params: { appName: "someName", appVersion: "someVersion" } }} />);
        detailedApp.setState(() => ({ error }));
        expect(detailedApp.state("error")).toBe(error);
        expect(detailedApp.find(Message).props().content).toBe(error);
        detailedApp.find(Message).find("i").simulate("click");
        expect(detailedApp.find(Message).exists()).toBe(false);
    });

    test("Component with application", () => {
        let detailedAppWrapper = mount(<MemoryRouter><DetailedApplication match={{ params: { appName: "someName", appVersion: "someVersion" } }} /></MemoryRouter>);
        detailedAppWrapper = detailedAppWrapper.update();
        detailedAppWrapper.find(DetailedApplication).instance().setState({ appInformation: detailedApplication, loading: false });
        expect(detailedAppWrapper.html()).toMatchSnapshot();
    });
});