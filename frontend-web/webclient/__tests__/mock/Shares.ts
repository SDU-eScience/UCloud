import {SharesByPath, ShareState} from "../../app/Shares";
import {AccessRight} from "../../app/Types";

export const shares: Page<SharesByPath> = {
    itemsInTotal: 1,
    itemsPerPage: 10,
    pageNumber: 0,
    items: [
        {
            path: "/home/j@h.com/AdEoA_Blackboard.pdf",
            sharedBy: "j@h.com",
            sharedByMe: true,
            shares: [{
                sharedWith: "user3@test.dk",
                rights: [AccessRight.WRITE],
                state: ShareState.ACCEPTED
            }]
        }],
    pagesInTotal: 0
};

test("", () => undefined);
