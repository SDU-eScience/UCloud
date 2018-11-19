import * as React from "react";
import Search from "Search/Search";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux"
import { initNotifications, initSimpleSearch } from "DefaultObjects";
import notifications from "Notifications/Redux/NotificationsReducer";
import { configure } from "enzyme";
import simpleSearch from "Search/Redux/SearchReducer";
import * as Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });

test("FIXME", () => undefined);