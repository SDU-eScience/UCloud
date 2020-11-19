import * as React from "react";
import {Instructions} from "../../app/WebDav/Instructions";
import {create} from "react-test-renderer";

test("Mount instructions", () => {
    expect(create(<Instructions token="foo" />)).toMatchSnapshot();
});
