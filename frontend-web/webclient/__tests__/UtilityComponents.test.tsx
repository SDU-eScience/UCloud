import {dialogStore} from "../app/Dialog/DialogStore";
import {
    addStandardDialog,
} from "../app/UtilityComponents";

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
});
