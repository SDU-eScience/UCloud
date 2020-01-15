import {configure, mount} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import View from "../../app/Applications/View";
import {detailedApplication} from "../mock/Applications";

configure({adapter: new Adapter()});

describe("Detailed application", () => {
    test.skip("Mount component", () => {
        expect(create(
            <View
                match={{
                    params: {
                        appName: "someName",
                        appVersion: "someVersion"
                    },
                    isExact: true,
                    path: "",
                    url: ""
                }}
            />
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Set error message", () => {
        const error = "Error Message!";
        const detailedApp = mount(
            <View
                match={{
                    params: {
                        appName: "someName",
                        appVersion: "someVersion"
                    },
                    isExact: true,
                    path: "",
                    url: ""
                }}
            />
        );
        detailedApp.setState(() => ({error}));
        expect(detailedApp.state("error")).toBe(error);
        expect(detailedApp.find("Message").props().content).toBe(error);
        detailedApp.find("Message").find("i").simulate("click");
        expect(detailedApp.find("Message").exists()).toBe(false);
    });

    test.skip("Component with application", () => {
        let detailedAppWrapper = mount(
            <MemoryRouter>
                <View
                    match={{
                        params: {
                            appName: "someName",
                            appVersion: "someVersion"
                        },
                        isExact: true,
                        path: "",
                        url: ""
                    }}
                />
            </MemoryRouter>
        );
        detailedAppWrapper.find(View).instance().setState({
            appInformation: detailedApplication,
            loading: false,
            complete: true
        });
        detailedAppWrapper = detailedAppWrapper.update();
        expect(detailedAppWrapper.html()).toMatchSnapshot();
    });
});
