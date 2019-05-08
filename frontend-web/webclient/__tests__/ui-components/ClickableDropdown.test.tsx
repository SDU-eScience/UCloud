import * as React from "react";
import ClickableDropdown from "../../app/ui-components/ClickableDropdown"
import { create } from "react-test-renderer";
import { ThemeProvider } from "styled-components";
import { theme } from "../../app/ui-components";
import "jest-styled-components";

describe("ClickableDropdown", () => {
    it("Closed clickable dropdown", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <ClickableDropdown
                    trigger={<button></button>}
                >child</ClickableDropdown>
            </ThemeProvider>)).toMatchSnapshot()
    });
});