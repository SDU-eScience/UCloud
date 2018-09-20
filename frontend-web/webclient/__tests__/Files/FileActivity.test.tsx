import * as React from "react";
import FileActivity, { ActivityFeed } from "Files/FileActivity";
import { create } from "react-test-renderer";
import { initAnalyses } from "DefaultObjects";
import analyses from "Applications/Redux/AnalysesReducer";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux";
import { activityPage } from "../mock/Activity";
import { MemoryRouter } from "react-router";

describe("FileActivity", () => {
    test("Mount", () => {
        const store = configureStore({ analyses: initAnalyses() }, { analyses })
        expect(create(<Provider store={store}><FileActivity /></Provider>).toJSON()).toMatchSnapshot();
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