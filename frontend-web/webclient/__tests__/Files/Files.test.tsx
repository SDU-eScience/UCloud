import * as React from "react";
import * as Renderer from "react-test-renderer";
import { emptyPage, KeyCode } from "DefaultObjects";
import Files, { FilesTable, FileOperations } from "Files/Files";
import { DropdownItem, Table, Input } from "semantic-ui-react";
import { setLoading } from "Files/Redux/FilesActions"
import { SortOrder, SortBy, Operation, PredicatedOperation } from "Files";
import { mockFiles_SensitivityConfidential } from "../mock/Files"
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { createMemoryHistory } from "history";
import files from "Files/Redux/FilesReducer";
import { Button, Dropdown } from "semantic-ui-react";
import { AllFileOperations } from "Utilities/FileUtilities";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import * as Enzyme from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";

Enzyme.configure({ adapter: new Adapter() });

const emptyPageStore = configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files });

const mockHistory = createMemoryHistory();

const fullPageStore = {
    ...emptyPageStore
};

fullPageStore.getState().files.page = mockFiles_SensitivityConfidential;

const nullOp = () => null;

const fileOperations = AllFileOperations(true, {
    setFileSelectorCallback: nullOp,
    showFileSelector: nullOp,
    setDisallowedPaths: nullOp,
    fetchPageFromPath: nullOp
}, nullOp, mockHistory);

describe("FilesTable", () => {
    test("Render empty", () => {
        expect(Renderer.create(
            <FilesTable
                files={emptyPage.items}
                fileOperations={[]}
                sortOrder={SortOrder.ASCENDING}
                sortingColumns={[SortBy.PATH, SortBy.MODIFIED_AT]}
                sortFiles={() => null}
                onCheckFile={() => null}
                refetchFiles={() => null}
                sortBy={SortBy.PATH}
                onFavoriteFile={() => null}
            />
        ).toJSON()).toMatchSnapshot();
    });

    test("Render non-empty", () => {
        expect(Renderer.create(
            <MemoryRouter>
                <FilesTable
                    files={mockFiles_SensitivityConfidential.items}
                    fileOperations={[]}
                    sortOrder={SortOrder.ASCENDING}
                    sortingColumns={[SortBy.PATH, SortBy.MODIFIED_AT]}
                    sortFiles={() => null}
                    onCheckFile={() => null}
                    refetchFiles={() => null}
                    sortBy={SortBy.PATH}
                    onFavoriteFile={() => null}
                />
            </MemoryRouter>
        ).toJSON()).toMatchSnapshot()
    })
});

describe("Files-component", () => {
    test("Full Files component, no files", () => {
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        match={{ params: [], isExact: false, path: "", url: "home" }}
                    />
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

    test("Full Files component, full page of files", () => {
        expect(Renderer.create(
            <Provider store={fullPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        match={{ params: [], isExact: false, path: "", url: "home" }}
                    />
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });
});


describe("File operations", () => {
    test("Empty files list, button, empty FilesOperations", () => {
        expect(Renderer.create(
            <FileOperations
                fileOperations={[]}
                files={emptyPageStore.getState().files.page.items}
                As={Button}
                fluid
                basic
            />
        ).toJSON()).toMatchSnapshot()
    });

    test("Empty files list, dropdown.item, empty FilesOperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={[]}
                files={emptyPageStore.getState().files.page.items}
                As={Dropdown.Item}
            />
        ).toJSON()).toMatchSnapshot()
    );

    test("Files list with items, button, empty FilesOperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={[]}
                files={fullPageStore.getState().files.page.items}
                As={Button}
                fluid
                basic
            />
        ).toJSON()).toMatchSnapshot()
    );

    test("Files list with items, dropdown.item, empty FilesOperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={[]}
                files={fullPageStore.getState().files.page.items}
                As={Button}
                fluid
                basic
            />
        ).toJSON()).toMatchSnapshot()
    );

    test("Empty files list, button, some fileoperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={fileOperations}
                files={emptyPageStore.getState().files.page.items}
                As={Button}
                fluid
                basic
            />
        )).toMatchSnapshot()
    );

    test("Empty files list, dropdown.item, some fileoperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={fileOperations}
                files={emptyPageStore.getState().files.page.items}
                As={Dropdown.Item}
                fluid
                basic
            />
        )).toMatchSnapshot()
    );

    test("Files list with items, button, some fileoperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={fileOperations}
                files={fullPageStore.getState().files.page.items}
                As={Button}
                fluid
                basic
            />
        )).toMatchSnapshot()
    );

    test("Files list with items, dropdown.item, some fileoperations", () =>
        expect(Renderer.create(
            <FileOperations
                fileOperations={fileOperations}
                files={fullPageStore.getState().files.page.items}
                As={Dropdown.Item}
            />
        )).toMatchSnapshot()
    );
});


const getOperationFrom = (node: Renderer.ReactTestRenderer, operation: string) =>
    node.root.findAllByType(FileOperations)[0].props.fileOperations.find((it: Operation) => it.text === operation);

const getPredicatedOperationFrom = (node: Renderer.ReactTestRenderer, operation: string, onTrue: boolean) =>
    node.root.findAllByType(FileOperations)[0].props.fileOperations.filter(it => it.onTrue).find((it: PredicatedOperation) => it[onTrue ? "onTrue" : "onFalse"].text === operation);

describe("FilesTable Operations being mounted", () => {
    // Non-predicated operations
    ["Share", "Download", "Copy", "Move", "Delete", "Properties"].forEach(operation =>
        test(`${operation} is rendered as dropdown`, function () {
            const node = Renderer.create(
                <Provider store={fullPageStore}>
                    <MemoryRouter>
                        <Files
                            history={mockHistory}
                            match={{ params: [], isExact: false, path: "", url: "home" }}
                        />
                    </MemoryRouter>
                </Provider>)
            const op: Operation = getOperationFrom(node, operation);
            const toMatch = (fileOperations.filter((it: Operation) => it.text === operation)[0] as Operation);
            // FIXME Currently needed due to dynamic nature of tests;
            expect(JSON.parse(JSON.stringify(op))).toEqual(JSON.parse(JSON.stringify(toMatch)));
        }));

    test("Rename", () => {
        const operationName = "Rename";
        const table = Renderer.create(
            <Provider store={fullPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        match={{ params: [], isExact: false, path: "", url: "home" }}
                    />
                </MemoryRouter>
            </Provider>);
        const operation = getOperationFrom(table, operationName);
        expect(operation).toBeDefined()
    });

    test("Create Project", () => {
        const operationName = "Create Project";
        const table = Renderer.create(
            <MemoryRouter>
                <FilesTable
                    files={fullPageStore.getState().files.page.items}
                    fileOperations={fileOperations}
                    sortOrder={SortOrder.ASCENDING}
                    sortingColumns={[SortBy.PATH, SortBy.MODIFIED_AT]}
                    sortFiles={() => null}
                    onCheckFile={() => null}
                    refetchFiles={() => null}
                    sortBy={SortBy.PATH}
                    onFavoriteFile={() => null}
                />
            </MemoryRouter>);
        const operation = getPredicatedOperationFrom(table, operationName, false);
        expect(operation).toBe(fileOperations.filter((it: PredicatedOperation) => it.onTrue).find((it: PredicatedOperation) => it["onFalse"].text === operationName));
    });

    test("Edit Project", () => {
        const operationName = "Edit Project";
        const table = Renderer.create(
            <MemoryRouter>
                <FilesTable
                    files={fullPageStore.getState().files.page.items}
                    fileOperations={fileOperations}
                    sortOrder={SortOrder.ASCENDING}
                    sortingColumns={[SortBy.PATH, SortBy.MODIFIED_AT]}
                    sortFiles={() => null}
                    onCheckFile={() => null}
                    refetchFiles={() => null}
                    sortBy={SortBy.PATH}
                    onFavoriteFile={() => null}
                />
            </MemoryRouter>);
        const operation = getPredicatedOperationFrom(table, operationName, true);
        expect(operation).toBe(fileOperations.filter((it: PredicatedOperation) => it.onTrue).find((it: Operation) => it["onTrue"].text === operationName));
    });
});

describe("FilesTable Operations being used", () => {
    test("Start and stop renaming", () => {
        let node = Enzyme.mount(
            <Provider store={fullPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        match={{ params: [], isExact: false, path: "/This/Is/A/Path", url: "home" }}
                    />
                </MemoryRouter>
            </Provider>);
        const firstBeingRenamedCount = fullPageStore.getState().files.page.items.filter(it => it.beingRenamed).length;
        expect(firstBeingRenamedCount).toBe(0);
        node.find(DropdownItem).findWhere(it => it.props().content === "Rename").first().simulate("click");
        
        // FIXME Must set loading as false as the component tries to fetch new page, I think
        fullPageStore.dispatch(setLoading(false));

        expect(fullPageStore.getState().files.page.items.filter(it => it.beingRenamed).length).toBe(1);
        node = node.update();
        
        node.find("input").findWhere(it => it.props().type === "text").simulate("keydown", {
             target: { value: "New folder Name" }, keyCode: KeyCode.ESC }
        );
        node = node.update();
        
        expect(fullPageStore.getState().files.page.items.filter(it => it.beingRenamed).length).toBe(0);
    });
});