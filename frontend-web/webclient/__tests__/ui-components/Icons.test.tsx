import * as React from "react";
import {create} from "react-test-renderer";
import {EveryIcon} from "../../app/ui-components/Icon"

it("Every Icon", () => {
    expect(create(<EveryIcon />).toJSON()).toMatchSnapshot();
});
