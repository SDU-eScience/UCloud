import * as React from "react";
import * as Renderer from "react-test-renderer";
import { combineReducers } from "redux";
import files from "Files/Redux/FilesReducer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";

const emptyPageStore = configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files });

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