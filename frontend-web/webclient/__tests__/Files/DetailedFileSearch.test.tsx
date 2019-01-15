import * as React from "react";
import * as Renderer from "react-test-renderer";
import DetailedFileSearch from "Files/DetailedFileSearch";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import files from "Files/Redux/FilesReducer";
import { mount, configure } from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as moment from "moment";
import { DatePicker } from "ui-components/DatePicker"
import { Input, Button, Label } from "ui-components";
import "jest-styled-components";

configure({ adapter: new Adapter() });

const store = configureStore({ files: initFiles("/home/person@place.tv") }, { files });