import * as React from "react";
import {Instructions} from "WebDav/Instructions";
import {create} from "react-test-renderer";

test("Mount instructions", () => {
    expect(create(<Instructions token="foo" />)).toMatchSnapshot();
});
