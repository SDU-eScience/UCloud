import * as React from "react";
import { PP, FileIcon, Arrow } from "../app/UtilityComponents";
import { iconFromFilePath } from "../app/UtilityFunctions"
import { newMockFolder } from "../app/Utilities/FileUtilities"
import { configure, shallow } from "enzyme";
import { create } from "react-test-renderer";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";
import { theme } from "../app/ui-components";
import { ThemeProvider } from "styled-components";

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

describe("FileIcon", () => {

    test("FileIcon, not link or shared", () => {
        const mockFile = newMockFolder();
        const iconType = iconFromFilePath(mockFile.path, mockFile.fileType, "/home/mail@mailhost.dk");
        expect(create(<FileIcon
            fileIcon={iconType}
        />)).toMatchSnapshot();
    });

    test("FileIcon, link", () => {
        const mockFile = newMockFolder();
        const iconType = iconFromFilePath(mockFile.path, mockFile.fileType, "/home/mail@mailhost.dk");
        expect(create(
            <ThemeProvider theme={theme}>
                <FileIcon
                    fileIcon={iconType}
                    link
                />
            </ThemeProvider>)).toMatchSnapshot();
    });
    test("FileIcon, shared", () => {
        const mockFile = newMockFolder();
        const iconType = iconFromFilePath(mockFile.path, mockFile.fileType, "/home/mail@mailhost.dk");
        expect(create(
            <ThemeProvider theme={theme}>
                <FileIcon
                    fileIcon={iconType}
                    shared
                />
            </ThemeProvider>)).toMatchSnapshot();
    });
});

describe("Arrow", () => {
    test("arrowUp", () => {
        expect(create(<Arrow name="arrowUp" />)).toMatchSnapshot();
    });

    test("arrowDown", () => {
        expect(create(<Arrow name="arrowDown" />)).toMatchSnapshot();
    });

    test("undefined", () => {
        expect(create(<Arrow name={undefined} />)).toMatchSnapshot();
    });
})