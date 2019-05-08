import * as React from "react";
import FilesTable from "../../app/Files/FilesTable";
import { SortOrder, SortBy } from "../../app/Files";

describe("FilesTable", () => {
    test("Mount", () => {
        const filesTable = <FilesTable 
            sortOrder={SortOrder.ASCENDING}
            sortBy={SortBy.PATH}
            sortingColumns={[SortBy.ACL, SortBy.CREATED_AT]}
            files={[]}
            refetchFiles={() => undefined}
            sortFiles={() => undefined}
            onCheckFile={() => undefined}
            fileOperations={[]}
        />;
        expect(filesTable).toMatchSnapshot();
    })
});