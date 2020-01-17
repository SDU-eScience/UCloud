import * as React from "react";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {theme} from "../../app/ui-components";
import ClickableDropdown from "../../app/ui-components/ClickableDropdown"

describe("ClickableDropdown", () => {
    it("Closed clickable dropdown", () => {
        expect(create(
            <ThemeProvider theme={theme}>
                <ClickableDropdown
                    trigger={<button />}
                >
                    child
                </ClickableDropdown>
            </ThemeProvider>
        )).toMatchSnapshot();
    });
});
