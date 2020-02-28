import {AnyAction} from "redux";
import * as NotificationsActions from "../../../app/Notifications/Redux/NotificationsActions";
import {store} from "../../../app/Utilities/ReduxUtilities";
import {notifications as mockNotifications} from "../../mock/Notifications";

describe("Notifications Actions", () => {
    test("Receive notifications", () => {
        const storeCopy = {...store};
        expect(storeCopy.getState().notifications.items.length).toBe(0);
        storeCopy.dispatch(NotificationsActions.receiveNotifications(mockNotifications));
        expect(storeCopy.getState().notifications.items.length).toBe(mockNotifications.items.length);
    });

    test.skip("Read notification", async () => {
        const storeCopy = {...store};
        storeCopy.dispatch(NotificationsActions.receiveNotifications(mockNotifications));
        expect(storeCopy.getState().notifications.items[0].read).toBe(false);
        const action = await NotificationsActions.notificationRead(mockNotifications.items[0].id);
        storeCopy.dispatch(action);
        expect(storeCopy.getState().notifications.items[0].read).toBe(true);
    });
});
