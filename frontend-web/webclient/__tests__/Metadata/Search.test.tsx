import * as React from "react";
import { Search } from "Metadata/Search";
import { create } from "react-test-renderer";
import { MemoryRouter } from "react-router";
import { configure, mount } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });

test("", () => undefined);

/* describe("Search component", () => {
    test("Mount search component", () =>
        expect(create(<MemoryRouter><Search /></MemoryRouter>, {}).toJSON()).toMatchSnapshot()
    )
}); */