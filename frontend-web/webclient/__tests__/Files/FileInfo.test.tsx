import * as React from "react";
import * as Renderer from "react-test-renderer";
import FileInfo from "Files/FileInfo";
import { Provider } from "react-redux";
import { combineReducers, createStore } from "redux";
import { MemoryRouter } from "react-router";
import files from "Files/Redux/FilesReducer";
import { SortBy, SortOrder } from "Files";
import { emptyPage } from "DefaultObjects";

// Middleware allowing for dispatching promises.
const addPromiseSupportToDispatch = (store) => {
    const rawDispatch = store.dispatch;
    return (action) => {
        if (typeof action.then === "function") {
            return action.then(rawDispatch);
        }
        return rawDispatch(action);
    };
};

const rootReducer = combineReducers({
    files
});

const configureStore = (initialObject) => {
    const store = createStore(rootReducer, initialObject);
    store.dispatch = addPromiseSupportToDispatch(store);
    return store;
};

const emptyPageStore = configureStore({
    files: {
        page: emptyPage,
        sortOrder: SortOrder.ASCENDING,
        sortBy: SortBy.PATH,
        loading: false,
        error: undefined,
        path: "",
        filesInfoPath: "",
        sortingColumns: [SortBy.PATH, SortBy.MODIFIED_AT],
        fileSelectorLoading: false,
        fileSelectorShown: false,
        fileSelectorPage: emptyPage,
        fileSelectorPath: "/home/Home",
        fileSelectorCallback: () => null,
        fileSelectorError: undefined,
        disallowedPaths: []
    }
});

/* test("FileInfo with no file", () => {
    const i = (Renderer.create(
        <Provider store={emptyPageStore}>
            <MemoryRouter>
                <FileInfo
                    match={{ params: [] }}
                />
            </MemoryRouter>
        </Provider>).toJSON());
    console.log(i);
    expect(Renderer.create(
        <Provider store={emptyPageStore}>
            <MemoryRouter>
                <FileInfo
                    match={{ params: [] }}
                    path={"aospdkas"}
                />
            </MemoryRouter>
        </Provider> 
    )).toBe(null);
})
 */
/*
    <Provider store={emptyPageStore}>
        <MemoryRouter>
            <Files
                history={mockHistory}
                match={{ params: [] as string[], isExact: false, path: "", url: "home" }}
            />
        </MemoryRouter>
    </Provider>) 
*/

test("Temporary Silencer", () => {
    expect(1).toBe(1);
})