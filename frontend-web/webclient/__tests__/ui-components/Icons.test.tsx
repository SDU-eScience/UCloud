import * as React from "react";
import { EveryIcon } from "ui-components/Icon"
import { create } from "react-test-renderer";
import "jest-styled-components";

it("Every Icon", () => {
    expect(create(<EveryIcon />).toJSON()).toMatchSnapshot();
});