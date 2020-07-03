import {configure, shallow} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {Client} from "../app/Authentication/HttpClientInstance";
import {dialogStore} from "../app/Dialog/DialogStore";
import {SortBy, SortOrder} from "../app/Files";
import {theme} from "../app/ui-components";
import {mockFile} from "../app/Utilities/FileUtilities";
import {store} from "../app/Utilities/ReduxUtilities";
import {
    addStandardDialog,
    Arrow,
    FileIcon,
    overwriteDialog,
    PP,
    rewritePolicyDialog,
    sensitivityDialog,
    shareDialog,
    SharePrompt
} from "../app/UtilityComponents";
import {iconFromFilePath} from "../app/UtilityFunctions";

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
        expect((pP.state() as {duration: number}).duration).toBe(500);
    });
});

describe("FileIcon", () => {

    test("FileIcon, not shared", () => {
        const mFile = mockFile({path: "path", type: "DIRECTORY"});
        const iconType = iconFromFilePath(mFile.path, mFile.fileType);
        expect(create(
            <ThemeProvider theme={theme}>
                <FileIcon fileIcon={iconType} />
            </ThemeProvider>
        )).toMatchSnapshot();
    });
    test("FileIcon, shared", () => {
        const mFile = mockFile({path: "path", type: "DIRECTORY"});
        const iconType = iconFromFilePath(mFile.path, mFile.fileType);
        expect(create(
            <ThemeProvider theme={theme}>
                <FileIcon
                    fileIcon={iconType}
                    shared
                />
            </ThemeProvider>
        )).toMatchSnapshot();
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
});


describe("Dialogs", () => {
    test("Add standard dialog", () => {
        let dialogCount = 0;
        dialogStore.subscribe(dialogs => dialogCount = dialogs.length);
        addStandardDialog({
            title: "Title",
            message: "Message",
            onConfirm: () => undefined
        });
        expect(dialogCount).toBe(1);
        dialogStore.failure();
        expect(dialogCount).toBe(0);
    });

    test("Add sensitivity dialog", () => {
        let dialogCount = 0;
        dialogStore.subscribe(dialogs => dialogCount = dialogs.length);
        sensitivityDialog();
        expect(dialogCount).toBe(1);
        dialogStore.failure();
        expect(dialogCount).toBe(0);
    });

    test("Add share dialog", () => {
        let dialogCount = 0;
        dialogStore.subscribe(dialogs => dialogCount = dialogs.length);
        shareDialog([], Client);
        expect(dialogCount).toBe(1);
        dialogStore.failure();
        expect(dialogCount).toBe(0);
    });

    test("Add overwrite dialog", () => {
        let dialogCount = 0;
        dialogStore.subscribe(dialogs => dialogCount = dialogs.length);
        overwriteDialog();
        expect(dialogCount).toBe(1);
        dialogStore.failure();
        expect(dialogCount).toBe(0);
    });

    test("Add rewritePolicy dialog, no overwrite", () => {
        let dialogCount = 0;
        dialogStore.subscribe(dialogs => dialogCount = dialogs.length);
        rewritePolicyDialog({
            allowOverwrite: false,
            filesRemaining: 0,
            client: Client,
            path: "path",
            projects: []
        });
        expect(dialogCount).toBe(1);
        dialogStore.failure();
        expect(dialogCount).toBe(0);
    });

    test("Add rewritePolicy dialog, no overwrite", () => {
        let dialogCount = 0;
        dialogStore.subscribe(dialogs => dialogCount = dialogs.length);
        rewritePolicyDialog({
            allowOverwrite: true,
            filesRemaining: 0,
            client: Client,
            path: "path",
            projects: []
        });
        expect(dialogCount).toBe(1);
        dialogStore.failure();
        expect(dialogCount).toBe(0);
    });
});

test("Share prompt", () => {
    expect(create(
        <Provider store={store}>
            <ThemeProvider theme={theme}>
                <SharePrompt paths={[""]} client={Client} />
            </ThemeProvider>
        </Provider>
    ).toJSON()).toMatchSnapshot();
});
