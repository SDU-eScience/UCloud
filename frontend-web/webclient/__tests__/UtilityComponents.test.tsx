import * as React from "react";
import { FileIcon, RefreshButton, WebSocketSupport, PP } from "UtilityComponents";
import { configure, shallow } from "enzyme";
import { create } from "react-test-renderer";
import * as Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });


describe("FileIcon", () => {
    test("Non-link file icon", () => {
        expect(create(
            <FileIcon
                name="add circle"
                size="mini"
                link={false}
                className=""
            />).toJSON()).toMatchSnapshot();
    });

    test("Non-link file icon, using defaults", () => {
        expect(create(
            <FileIcon
                name="add circle"
                size="mini"
            />).toJSON()).toMatchSnapshot();
    });

    test("Link file icon", () => {
        expect(create(
            <FileIcon
                link={true}
                name="add circle"
                size="mini"
            />).toJSON()).toMatchSnapshot();
    });
});

describe("RefreshButton", () => {
    test("RefreshButton", () => {
        expect(create(
            <RefreshButton
                loading={false}
                onClick={() => null}
                className="name-of-class"
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