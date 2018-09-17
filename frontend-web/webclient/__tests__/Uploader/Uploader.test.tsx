import * as React from "react";
import { create } from "react-test-renderer";
import { shallow, configure } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import { Uploader } from "Uploader";

configure({ adapter: new Adapter() });


describe("Uploader", () => {
    test("", () => {
        //const uploader = create(<Uploader />);
        expect(1).toBe(1);
    })
});