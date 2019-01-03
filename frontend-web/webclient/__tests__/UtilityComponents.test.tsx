import * as React from "react";
import { FileIcon, RefreshButton, WebSocketSupport, PP } from "UtilityComponents";
import { configure, shallow } from "enzyme";
import { create } from "react-test-renderer";
import * as Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });


describe("RefreshButton", () => {
    test("RefreshButton", () => {
        expect(create(
            <RefreshButton
                loading={false}
                onClick={() => null}
            />).toJSON()).toMatchSnapshot()
    });

    test("RefreshButton", () => {
        let fun = jest.fn();
        const button = shallow(
            <RefreshButton
                loading={false}
                onClick={fun}
            />)
        button.simulate("click");
        expect(fun).toHaveBeenCalled();
    });
});

describe("WebSocket support", () => {
    test("Websocket component", () => {
        expect(create(<WebSocketSupport />).toJSON()).toMatchSnapshot();
    });
});

describe("PP", () => {

    test("Non-visible PP-Component", () => {
        expect(create(<PP visible={false} />).toJSON()).toMatchSnapshot();
    });

    test("Visible PP-Component", () => {
        expect(create(<PP visible={true} />).toJSON()).toMatchSnapshot();
    });

    test("Change PP-value", () => {
        const pP = shallow(<PP visible={true} />);
        pP.findWhere(it => it.type() === "input").simulate("change", { target: { value: "500" } });
        expect(pP.state()["duration"]).toBe(500);
    });
});