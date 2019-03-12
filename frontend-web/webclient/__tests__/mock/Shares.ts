import { SharesByPath, ShareState } from "Shares";
import { Page, AccessRight } from "Types";

export const shares: Page<SharesByPath> = {
    itemsInTotal: 1,
    itemsPerPage: 10,
    pageNumber: 0,
    items: [
        {
            path: "/home/jonas@hinchely.dk/AdEoA_Blackboard.pdf",
            sharedBy: "jonas@hinchely.dk",
            sharedByMe: true,
            shares: [{
                id: "3",
                sharedWith: "user3@test.dk",
                rights: [AccessRight.WRITE, AccessRight.EXECUTE],
                state: ShareState.ACCEPTED
            }]
        }],
    pagesInTotal: 0
}

test("", () => undefined);