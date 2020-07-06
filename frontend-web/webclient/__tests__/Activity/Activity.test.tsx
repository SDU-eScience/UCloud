import * as React from "react";
import {Provider} from "react-redux";
import {create, act} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Activity from "../../app/Activity/Page";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

const result = {
    endOfScroll: true,
    items: [{
        type: "directoryCreated", timestamp: 1592994314640, activityEvent: {
            username: "jonas", timestamp: 1592994314640, filePath: "/home/jonas/folder in home"
        }
    }, {
        type: "copy", timestamp: 1589291813490, activityEvent: {
            username: "jonas", timestamp: 1589291813490, filePath: "/home/jonas/IMG_20191206_091915.jpg", copyFilePath: "/projects/Project 2/Repo/IMG_20191206_091915.jpg"
        }
    }, {
        type: "moved", timestamp: 1589277456488, activityEvent: {
            username: "jonas", newName: "/home/jonas/Foo/IMG_20191206_091915.jpg", timestamp: 1589277456488, filePath: "/home/jonas/IMG_20191206_091915.jpg"
        }
    }, {
        type: "directoryCreated", timestamp: 1589277447937, activityEvent: {
            username: "jonas", timestamp: 1589277447937, filePath: "/home/jonas/Foo"
        }
    }, {
        type: "copy", timestamp: 1589274590493, activityEvent: {
            username: "jonas", timestamp: 1589274590493, filePath: "/home/jonas/IMG_20191206_091915.jpg", copyFilePath: "/projects/Project 2/Repo/IMG_20191206_091915.jpg"
        }
    }, {
        type: "download", timestamp: 1589201528682, activityEvent: {
            username: "jonas", timestamp: 1589201528682, filePath: "/home/jonas/IMG_20191206_091915.jpg"
        }
    }, {
        type: "upload", timestamp: 1589187168844, activityEvent: {
            username: "jonas", timestamp: 1589187168844, filePath: "/home/jonas/IMG_20191206_091915.jpg"
        }
    }]
}

jest.mock("Authentication/HttpClientInstance", () => ({
    Client: {
        get: () => result,
        hasActiveProject: () => false
    }
}));

describe("Activity Page", () => {
    test("Mount Activity Page, without project", async () => {
        let container;
        await act(async () => {
            container = await create(
                <Provider store={store}>
                    <ThemeProvider theme={theme}>
                        <Activity />
                    </ThemeProvider>
                </Provider>
            );
        });
        expect(container).toMatchSnapshot();
    });
});
