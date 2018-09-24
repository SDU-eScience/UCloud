import * as React from "react";
import * as Renderer from "react-test-renderer";
import { BreadCrumbs } from "Breadcrumbs/Breadcrumbs";
import { configure, shallow } from "enzyme";
import Adapter from "enzyme-adapter-react-16";
import { Breadcrumb } from "semantic-ui-react";

configure({ adapter: new Adapter() });

describe("Breadcrumbs", () => {
    const homefolder = "/home/tester@user.dk/";
    test("Build breadcrumbs from empty string", () => {
        expect(Renderer.create(
            <BreadCrumbs
                currentPath=""
                navigate={() => null}
                homeFolder={homefolder}
            />).toJSON()).toMatchSnapshot()
    });

    test("Build breadcrumbs from string without home in it", () => {
        expect(Renderer.create(
            <BreadCrumbs
                currentPath="Not/Home/Folder"
                navigate={() => null}
                homeFolder={homefolder}
            />).toJSON()).toMatchSnapshot()
    });

    test("Build breadcrumbs from string with home in it", () => {
        expect(Renderer.create(
            <BreadCrumbs
                currentPath={`${homefolder}followed/by/something/more`}
                navigate={() => null}
                homeFolder={homefolder}
            />).toJSON()).toMatchSnapshot()
    });

    test("Clicking navigate on active breadcrumb", () => {
        const navigate = jest.fn();
        const node = shallow(<BreadCrumbs currentPath={"/home/else/where/"} navigate={navigate} homeFolder={homefolder}/>);
        expect(navigate).toBeCalledTimes(0);
        node.findWhere(it => it.type() === Breadcrumb.Section).first().simulate("click")
        expect(navigate).toHaveBeenCalled();
    });
});