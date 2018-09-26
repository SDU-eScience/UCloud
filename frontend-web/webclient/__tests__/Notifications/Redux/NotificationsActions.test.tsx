import * as NotificationsActions from "Notifications/Redux/NotificationsActions";
import { configureStore } from "Utilities/ReduxUtilities";
import { initNotifications } from "DefaultObjects";
import notifications from "Notifications/Redux/NotificationsReducer";
import { notifications as mockNotifications } from "../../mock/Notifications"

describe("Notifications Actions", () => {
    test("Receive notifications", () => {
        const store = configureStore({ notifications: initNotifications() }, { notifications });
        expect(store.getState().notifications.page.items.length).toBe(0);
        store.dispatch(NotificationsActions.receiveNotifications(mockNotifications));
        expect(store.getState().notifications.page.items.length).toBe(mockNotifications.items.length);
    });

    test("Set Redirect", () => {
        const redirectTo = "/asd/asd/";
        const store = configureStore({ notifications: initNotifications() }, { notifications });
        store.dispatch(NotificationsActions.setRedirectTo(redirectTo));
        expect(store.getState().notifications.redirectTo).toEqual(redirectTo);
    });

    test("Read notification", () => {
        const store = configureStore({ notifications: initNotifications() }, { notifications });
        store.dispatch(NotificationsActions.receiveNotifications(mockNotifications));
        expect(store.getState().notifications.page.items[0].read).toBe(false);
        store.dispatch(NotificationsActions.notificationRead(mockNotifications.items[0].id));
        expect(store.getState().notifications.page.items[0].read).toBe(true);
    })
});