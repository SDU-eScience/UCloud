import * as React from "react";
import {PP, FileIcon, Arrow} from "../app/UtilityComponents";
import {iconFromFilePath} from "../app/UtilityFunctions"
import {mockFile} from "../app/Utilities/FileUtilities"
import {configure, shallow} from "enzyme";
import {create} from "react-test-renderer";
import * as Adapter from "enzyme-adapter-react-16";
import "jest-styled-components";
import {theme} from "../app/ui-components";
import {ThemeProvider} from "styled-components";
import {SortBy, SortOrder} from "../app/Files";

configure({adapter: new Adapter()});

describe("PP", () => {

    test("Non-visible PP-Component", () => {
        expect(create(<PP visible={false} />).toJSON()).toMatchSnapshot();
    });

    test("Visible PP-Component", () => {
        expect(create(<PP visible />).toJSON()).toMatchSnapshot();
    });

    test.skip("Change PP-value", () => {
        const pP = shallow(<PP visible />);
        pP.findWhere(it => !!it.props().type().range).simulate("change", {target: {value: "500"}});
        expect(pP.state()["duration"]).toBe(500);
    });
});

describe("FileIcon", () => {

    test("FileIcon, not shared", () => {
        const mFile = mockFile({path: "path", type: "DIRECTORY"});
        const iconType = iconFromFilePath(mFile.path, mFile.fileType, "/home/mail@mailhost.dk");
        expect(create(<FileIcon
            fileIcon={iconType}
        />)).toMatchSnapshot();
    });
    test("FileIcon, shared", () => {
        const mFile = mockFile({path: "path", type: "DIRECTORY"});
        const iconType = iconFromFilePath(mFile.path, mFile.fileType, "/home/mail@mailhost.dk");
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
        expect(create(
            <Arrow activeSortBy={SortBy.PATH} sortBy={SortBy.PATH} order={SortOrder.ASCENDING} />
        )).toMatchSnapshot();
    });

    test("arrowDown", () => {
        expect(create(
            <Arrow activeSortBy={SortBy.PATH} sortBy={SortBy.PATH} order={SortOrder.DESCENDING} />
        )).toMatchSnapshot();
    });

    test("undefined", () => {
        expect(create(
            <Arrow activeSortBy={SortBy.PATH} sortBy={SortBy.FILE_TYPE} order={SortOrder.ASCENDING} />)
        ).toMatchSnapshot();
    });
})