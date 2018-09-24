import * as React from "react";
import Activity, { ActivityFeed } from "Activity/Activity";
import { create } from "react-test-renderer";
import { initActivity } from "DefaultObjects";
import activity from "Activity/Redux/ActivityReducer";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux";
import { activityPage } from "../mock/Activity";
import { MemoryRouter } from "react-router";

describe("Activity", () => {
    test("Mount", () => {
        const store = configureStore({ activity: initActivity() }, { activity })
        expect(create(<Provider store={store}><Activity /></Provider>).toJSON()).toMatchSnapshot();
    });
});

describe("Activity Feed", () => {
    test("Mount, no activity", () => {
        expect(create(<ActivityFeed activity={[]} />))
    });

    test("Mount, with activity", () => {
        expect(create(<MemoryRouter><ActivityFeed activity={activityPage.items} /></MemoryRouter>))
    });
})