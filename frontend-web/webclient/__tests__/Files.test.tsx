import * as React from "react";
import * as Renderer from "react-test-renderer";
import { emptyPage } from "DefaultObjects"; 
import { FilesTable } from "Files/Files";
import { File, SortOrder, SortBy } from "Files";

test("Render empty files table", () => {
    const filestable = Renderer.create(
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
    ).toJSON();
    expect(filestable).toMatchSnapshot();
});