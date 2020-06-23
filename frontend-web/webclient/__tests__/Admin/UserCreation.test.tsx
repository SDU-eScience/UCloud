import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import UserCreation from "../../app/Admin/UserCreation";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";
import {render, screen, fireEvent, getByDisplayValue, waitForElement} from "@testing-library/react";

configure({adapter: new Adapter()});

const userCreation = () => (
    <Provider store={store}>
        <MemoryRouter>
            <ThemeProvider theme={theme}>
                <UserCreation />
            </ThemeProvider>
        </MemoryRouter>
    </Provider>
);

describe("UserCreation", () => {
    test("Mount", () => expect(create(userCreation()).toJSON()).toMatchSnapshot());

    test("Update username field", async () => {
        render(userCreation());
        const input = screen.getByLabelText("Username") as HTMLInputElement;
        expect(input.value).toBe("");
        fireEvent.change(input, {target: {value: "username"}});
        expect(input.value).toEqual("username");
    });

    test("Update password field", () => {
        render(userCreation());
        const input = screen.getByLabelText("Password") as HTMLInputElement;
        expect(input.value).toBe("");
        fireEvent.change(input, {target: {value: "password"}});
        expect(input.value).toEqual("password");
    });

    test("Update repeated password field", () => {
        render(userCreation());
        const input = screen.getByLabelText("Repeat password") as HTMLInputElement;
        expect(input.value).toBe("");
        fireEvent.change(input, {target: {value: "password"}});
        expect(input.value).toEqual("password");
    });

    test("Fill fields, check validity", () => {
        render(userCreation());
        const passwordInput = screen.getByLabelText("Password") as HTMLInputElement;
        expect(passwordInput.checkValidity()).toBe(false);
        fireEvent.change(passwordInput, {target: {value: "password"}});
        expect(passwordInput.checkValidity()).toBe(true);

        const repeatInput = screen.getByLabelText("Repeat password") as HTMLInputElement;
        expect(repeatInput.checkValidity()).toBe(false);
        fireEvent.change(repeatInput, {target: {value: "password"}});
        expect(repeatInput.checkValidity()).toBe(true);

        const usernameInput = screen.getByLabelText("Username") as HTMLInputElement;
        expect(usernameInput.checkValidity()).toBe(false);
        fireEvent.change(usernameInput, {target: {value: "username"}});
        expect(usernameInput.checkValidity()).toBe(true);

        const mailInput = screen.getByLabelText("Email") as HTMLInputElement;
        expect(mailInput.checkValidity()).toBe(false);
        fireEvent.change(mailInput, {target: {value: "mail"}});
        expect(mailInput.checkValidity()).toBe(false);
        fireEvent.change(mailInput, {target: {value: "mail@mail"}});
        expect(mailInput.checkValidity()).toBe(true);
    });
});
