import * as React from "react";
import * as Renderer from "react-test-renderer";
import { emptyPage, KeyCode } from "../../app/DefaultObjects";
import { updateFiles } from "../../app/Files/Redux/FilesActions";
import Files from "../../app/Files/Files";
import FilesTable, { FileOperations } from "../../app/Files/FilesTable";
import { setLoading } from "../../app/Files/Redux/FilesActions"
import { SortOrder, SortBy, File } from "../../app/Files";
import { mockFiles_SensitivityConfidential } from "../mock/Files"
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { createMemoryHistory } from "history";
import files from "../../app/Files/Redux/FilesReducer";
import { allFileOperations } from "../../app/Utilities/FileUtilities";
import { configureStore, responsive } from "../../app/Utilities/ReduxUtilities";
import { initFiles } from "../../app/DefaultObjects";
import { mount } from "enzyme";
import { Page, Operation, PredicatedOperation } from "../../app/Types";
import "jest-styled-components";
// import * as Adapter from "enzyme-adapter-react-16";
// 
// configure({ adapter: new Adapter() });

const createMockStore = (filesPage?: Page<File>) => {
    const store = configureStore({ files: initFiles("/home/user@test.abc/") }, {
        files,
        responsive
    })
    if (!!filesPage) {
        store.dispatch(updateFiles(filesPage));
    }
    return store;
}

const mockHistory = createMemoryHistory();
const nullOp = () => null;

const fileOperations = allFileOperations({
    stateless: true,
    fileSelectorOperations: {
        setFileSelectorCallback: nullOp,
        showFileSelector: nullOp,
        setDisallowedPaths: nullOp,
        fetchPageFromPath: nullOp,
        fetchFilesPage: nullOp
    },
    onDeleted: nullOp,
    onExtracted: nullOp,
    onClearTrash: nullOp,
    history: mockHistory,
    setLoading: nullOp,
});

describe("FilesTable", () => {
    test.skip("Render empty", () => {
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

    test.skip("Render non-empty", () => {
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
    test.skip("Full Files component, no files", () => {
        expect(Renderer.create(
            <Provider store={createMockStore()}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        location={{ search: "" }}
                    />
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

    test.skip("Full Files component, full page of files", () => {
        expect(Renderer.create(
            <Provider store={createMockStore(mockFiles_SensitivityConfidential)}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        location={{ search: "" }}
                    />
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });
});

const getOperationFrom = (node: Renderer.ReactTestRenderer, operation: string) =>
    node.root.findAllByType(FileOperations)[0].props.fileOperations.find((it: Operation<File>) => it.text === operation);

const getPredicatedOperationFrom = (node: Renderer.ReactTestRenderer, operation: string, onTrue: boolean) =>
    node.root.findAllByType(FileOperations)[0].props.fileOperations.filter(it => it.onTrue).find((it: PredicatedOperation<File>) => it[onTrue ? "onTrue" : "onFalse"].text === operation);

describe("FilesTable Operations being mounted", () => {
    // Non-predicated operations
    ["Share", "Download", "Copy", "Move", "Delete", "Properties"].forEach(operation =>
        test.skip(`${operation} is rendered as dropdown`, function () {
            const node = Renderer.create(
                <Provider store={createMockStore(mockFiles_SensitivityConfidential)}>
                    <MemoryRouter>
                        <Files
                            history={mockHistory}
                            location={{ search: "?" }}
                        />
                    </MemoryRouter>
                </Provider>)
            const op: Operation<File> = getOperationFrom(node, operation);
            const toMatch = (fileOperations.filter((it: Operation<File>) => it.text === operation)[0] as Operation<File>);
            // FIXME Currently needed due to dynamic nature of tests;
            expect(JSON.parse(JSON.stringify(op))).toEqual(JSON.parse(JSON.stringify(toMatch)));
        }));

    test.skip("Rename", () => {
        const operationName = "Rename";
        const table = Renderer.create(
            <Provider store={createMockStore(mockFiles_SensitivityConfidential)}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        location={{ search: "?" }}
                    />
                </MemoryRouter>
            </Provider>);
        const operation = getOperationFrom(table, operationName);
        expect(operation).toBeDefined()
    });

    test.skip("Create Project", () => {
        const operationName = "Create Project";
        const table = Renderer.create(
            <MemoryRouter>
                <FilesTable
                    files={createMockStore(mockFiles_SensitivityConfidential).getState().files.page.items}
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
        expect(operation).toBe(fileOperations.filter((it: PredicatedOperation<File>) => it.onTrue).find((it: PredicatedOperation<File>) => it["onFalse"].text === operationName));
    });

    test.skip("Edit Project", () => {
        const operationName = "Edit Project";
        const table = Renderer.create(
            <MemoryRouter>
                <FilesTable
                    files={createMockStore(mockFiles_SensitivityConfidential).getState().files.page.items}
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
        expect(operation).toBe(fileOperations.filter((it: PredicatedOperation<File>) => it.onTrue).find((it: Operation<File>) => it["onTrue"].text === operationName));
    });
});

describe("Files components usage", () => {

    test.skip("Start creation of folder", () => {
        const emptyPageStore = createMockStore();
        let files = mount(
            <Provider store={emptyPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        location={{ search: "?path=/This/Is/A/Path" }}
                    />
                </MemoryRouter>
            </Provider>);
        expect(emptyPageStore.getState().files.page.items.length).toBe(0);
        files.findWhere(it => it.props().content === "New folder").simulate("click");
        // Test cleaning up other mock folders
        files.findWhere(it => it.props().content === "New folder").simulate("click");
        emptyPageStore.dispatch(setLoading(false));
        files = files.update();
        expect(emptyPageStore.getState().files.page.items.length).toBe(1);
        files.findWhere(it => it.props().className === "ui red small basic button").simulate("click");
        expect(emptyPageStore.getState().files.page.items.every(it => !it.isMockFolder));
    });
});

describe("FilesTable Operations being used", () => {
    test.skip("Start and stop renaming", () => {
        const fullPageStore = createMockStore(mockFiles_SensitivityConfidential);
        let node = mount(
            <Provider store={fullPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        location={{ search: "?Path=/This/Is/A/Path" }}
                    />
                </MemoryRouter>
            </Provider>);
        const firstBeingRenamedCount = fullPageStore.getState().files.page.items.filter(it => it.beingRenamed).length;
        expect(firstBeingRenamedCount).toBe(0);
        node.find("DropdownItem").findWhere(it => it.type() === "span").findWhere(it => it.text() === "Rename").first().simulate("click");
        // FIXME Must set loading as false as the component tries to fetch new page, I think
        fullPageStore.dispatch(setLoading(false));

        expect(fullPageStore.getState().files.page.items.filter(it => it.beingRenamed).length).toBe(1);
        node = node.update();

        node.find("input").findWhere(it => it.props().type === "text").simulate("keydown", {
            keyCode: KeyCode.ESC,
            target: { value: "New folder Name" }
        });

        node = node.update();

        expect(fullPageStore.getState().files.page.items.filter(it => it.beingRenamed).length).toBe(0);
    });
});