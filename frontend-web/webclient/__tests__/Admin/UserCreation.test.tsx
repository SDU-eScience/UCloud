import UserCreation from "Admin/UserCreation";
import {initResponsive, initStatus} from "DefaultObjects";
import {configure, mount} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";
import status from "Navigation/Redux/StatusReducer";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {createResponsiveStateReducer} from "redux-responsive";
import {responsiveBP} from "ui-components/theme";
import {configureStore} from "Utilities/ReduxUtilities";

configure({adapter: new Adapter()});

const store = configureStore({status: initStatus(), responsive: initResponsive()}, {
    status,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"})
});

const userCreation = () =>
    <Provider store={store}>
        <MemoryRouter>
            <UserCreation/>
        </MemoryRouter>
    </Provider>

describe("UserCreation", () => {
    test("Mount", () => expect(create(userCreation()).toJSON()).toMatchSnapshot());

    test.skip("Update username field", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Username").find("input").simulate("change", {target: {value: "username"}});
        expect(uC.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "username",
            password: "",
            repeatedPassword: "",
            usernameError: false,
            passwordError: false
        });
    });

    test.skip("Update password field", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Password").find("input").simulate("change", {target: {value: "password"}});
        expect(uC.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "",
            password: "password",
            repeatedPassword: "",
            usernameError: false,
            passwordError: false
        });
    });

    test.skip("Update repeated password field", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", {target: {value: "repeatWord"}});
        expect(uC.state()).toEqual({
            promiseKeeper: new PromiseKeeper(),
            submitted: false,
            username: "",
            password: "",
            repeatedPassword: "repeatWord",
            usernameError: false,
            passwordError: false
        });
    });

    test.skip("Submit with missing username, causing errors to be rendered", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Password").find("input").simulate("change", {target: {value: "password"}});
        uC.find("FormField").findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", {target: {value: "password"}});
        uC.find("Button").findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(uC.state("usernameError")).toBe(true);
        expect(uC.state("passwordError")).toBe(false);
    });

    test.skip("Submit with missing password fields, causing errors to be rendered", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Username").find("input").simulate("change", {target: {value: "username"}});
        uC.find("Button").findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(uC.state("passwordError")).toBe(true);
        expect(uC.state("usernameError")).toBe(false);
    });

    test.skip("Submit with non matching password fields, causing errors to be rendered", () => {
        const uC = mount(userCreation());
        uC.find("FormField").findWhere(it => it.props().label === "Password").find("input").simulate("change", {target: {value: "passwordAlso"}});
        uC.find("FormField").findWhere(it => it.props().label === "Repeat password").find("input").simulate("change", {target: {value: "password"}});
        uC.find("Button").findWhere(it => it.props().content === "Create user").simulate("submit");
        expect(uC.state("usernameError")).toBe(true);
        expect(uC.state("passwordError")).toBe(true);
    });
});
