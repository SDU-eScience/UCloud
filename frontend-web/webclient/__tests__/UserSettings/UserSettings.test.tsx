import * as React from "react";
import UserSettings from "UserSettings/UserSettings";
import { create } from "react-test-renderer";
import { mount, configure } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";

configure({ adapter: new Adapter() });

describe("UserSettings", () => {
    test("Mount", () => {
        expect(create(<UserSettings />).toJSON()).toMatchSnapshot()
    });

    test.skip("Update current password field", () => {
        const userSettings = mount(<UserSettings />);
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Old password").find("input").simulate("change", { target: { value: "current password" } });
        expect(userSettings.state("currentPassword")).toBe("current password")
    });

    test.skip("Update new password field", () => {
        const userSettings = mount(<UserSettings />);
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "New password").find("input").simulate("change", { target: { value: "new password" } });
        expect(userSettings.state("newPassword")).toBe("new password")
    });

    test.skip("Update repeated password field", () => {
        const userSettings = mount(<UserSettings />);
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Repeat password").find("input").simulate("change", { target: { value: "repeated password" } });
        expect(userSettings.state("repeatedPassword")).toBe("repeated password")
    });

    test.skip("Submit with missing old password", () => {
        const userSettings = mount(<UserSettings />);
        const password = "password";
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "New password").find("input").simulate("change", { target: { value: password } });
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Repeat password").find("input").simulate("change", { target: { value: password } });
        userSettings.find("Button").findWhere(it => it.props().content === "Change password").simulate("submit");
        expect(userSettings.state("error")).toBe(true);
    });

    test.skip("Submit with missing password", () => {
        const userSettings = mount(<UserSettings />);
        const password = "password";
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Old password").find("input").simulate("change", { target: { value: "current password" } });
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Repeat password").find("input").simulate("change", { target: { value: password } });
        userSettings.find("Button").findWhere(it => it.props().content === "Change password").simulate("submit");
        expect(userSettings.state("error")).toBe(true);
    });

    test.skip("Submit with missing repeated password", () => {
        const userSettings = mount(<UserSettings />);
        const password = "password";
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Old password").find("input").simulate("change", { target: { value: "current password" } });
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "New password").find("input").simulate("change", { target: { value: password } });
        userSettings.find("Button").findWhere(it => it.props().content === "Change password").simulate("submit");
        expect(userSettings.state("error")).toBe(true);
    });

    test.skip("Submit with non-matching new passwords", () => {
        const userSettings = mount(<UserSettings />);
        const password = "password";
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Old password").find("input").simulate("change", { target: { value: "current password" } });
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "New password").find("input").simulate("change", { target: { value: "one password" } });
        userSettings.find("FormField").findWhere(it => it.props().placeholder === "Repeat password").find("input").simulate("change", { target: { value: "another password" } });
        userSettings.find("Button").findWhere(it => it.props().content === "Change password").simulate("submit");
        expect(userSettings.state("error") && userSettings.state("repeatPasswordError")).toBe(true);
    });
});