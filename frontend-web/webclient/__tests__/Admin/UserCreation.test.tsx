import * as React from "react";
import UserCreation from "Admin/UserCreation";
import { create } from "react-test-renderer";
import { configure, mount } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import { FormField, Button } from "semantic-ui-react";
import PromiseKeeper from "PromiseKeeper";

configure({ adapter: new Adapter() });

describe("UserCreation", () => {
    test("Mount", () =>
        expect(create(<UserCreation />).toJSON()).toMatchSnapshot()
    );

    test("Update username field", () => {
        const userCreation = mount(<UserCreation />);
        userCreation.find(FormField).findWhere(it => it.props().label === "Username").find("input").simulate("change", { target: { value: "username" } });
        expect(userCreation.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "username",
            password: "",
            repeatedPassword: "",
            usernameError: false,
            passwordError: false
        });
    });

    test("Update password field", () => {
        const userCreation = mount(<UserCreation />);
        userCreation.find(FormField).findWhere(it => it.props().label === "Password").find("input").simulate("change", { target: { value: "password" } });
        expect(userCreation.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "",
            password: "password",
            repeatedPassword: "",
            usernameError: false,
            passwordError: false
        });
    });

    test("Update repeated password field", () => {
        const userCreation = mount(<UserCreation />);
        userCreation.find(FormField).findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", { target: { value: "repeatWord" } });
        expect(userCreation.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "",
            password: "",
            repeatedPassword: "repeatWord",
            usernameError: false,
            passwordError: false
        });
    });

    test("Submit with missing username, causing errors to be rendered", () => {
        const userCreation = mount(<UserCreation />);
        userCreation.find(FormField).findWhere(it => it.props().label === "Password").find("input").simulate("change", { target: { value: "password" } });
        userCreation.find(FormField).findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", { target: { value: "password" } });
        userCreation.find(Button).findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(userCreation.state("usernameError")).toBe(true);
        expect(userCreation.state("passwordError")).toBe(false);
    });

    test("Submit with missing password fields, causing errors to be rendered", () => {
        const userCreation = mount(<UserCreation />);
        userCreation.find(FormField).findWhere(it => it.props().label === "Username").find("input").simulate("change", { target: { value: "username" } });
        userCreation.find(Button).findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(userCreation.state("passwordError")).toBe(true);
        expect(userCreation.state("usernameError")).toBe(false);
    });

    test("Submit with non matching password fields, causing errors to be rendered", () => {
        const userCreation = mount(<UserCreation />);
        userCreation.find(FormField).findWhere(it => it.props().label === "Password").find("input").simulate("change", { target: { value: "passwordAlso" } });
        userCreation.find(FormField).findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", { target: { value: "password" } });
        userCreation.find(Button).findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(userCreation.state("usernameError")).toBe(true);
        expect(userCreation.state("passwordError")).toBe(true);
    });
});
