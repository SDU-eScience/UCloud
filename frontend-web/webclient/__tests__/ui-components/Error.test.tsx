import * as React from "react";
import { Icon } from "../../app/ui-components"
import Error from "../../app/ui-components/Error";
import { create } from "react-test-renderer";
import { configure, mount } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";
configure({ adapter: new Adapter() });

describe("Error component", () => {

    it("Error without error and no dismiss button", () => {
        expect(create(<Error />).toJSON()).toMatchSnapshot();
    });

    it("Error with error and no dismiss button", () => {
        expect(create(<Error error="This is an error" />).toJSON()).toMatchSnapshot();
    });

    it("Error with error and dismiss button", () => {
        expect(create(<Error error="This is an error" clearError={() => undefined} />).toJSON()).toMatchSnapshot();
    });

    it("Dismissing error", () => {
        const fn = jest.fn();
        const error = mount(<Error error="This is an error" clearError={() => fn()} />);
        error.find(Icon).first().simulate("click", { stopPropagation: () => undefined });
        expect(fn).toBeCalledTimes(1);
    });
})