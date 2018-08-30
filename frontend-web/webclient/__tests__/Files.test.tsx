import * as React from "react";
import * as Renderer from "react-test-renderer";
import { emptyPage } from "DefaultObjects";
import { FilesTable } from "Files/Files";
import { File, SortOrder, SortBy } from "Files";
import { mockFiles_SensitivityConfidential } from "./mock/Files"
import { MemoryRouter } from "react-router-dom";

test("Render empty files table", () => {
    expect(Renderer.create(
        <FilesTable
            files={emptyPage.items as File[]}
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

test("Files in filestable", () => {
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
});