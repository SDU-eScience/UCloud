import * as React from "react";
import { PP } from "UtilityComponents";
import { configure, shallow } from "enzyme";
import { create } from "react-test-renderer";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";

configure({ adapter: new Adapter() });

describe("PP", () => {

    test("Non-visible PP-Component", () => {
        expect(create(<PP visible={false} />).toJSON()).toMatchSnapshot();
    });

    test("Visible PP-Component", () => {
        expect(create(<PP visible />).toJSON()).toMatchSnapshot();
    });

    test.skip("Change PP-value", () => {
        const pP = shallow(<PP visible />);
        pP.findWhere(it => !!it.props().type().range).simulate("change", { target: { value: "500" } });
        expect(pP.state()["duration"]).toBe(500);
    });
});