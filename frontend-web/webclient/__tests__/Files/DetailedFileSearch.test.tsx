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
import "react-datepicker/dist/react-datepicker.css";
import { Input, Button, Label } from "ui-components";

configure({ adapter: new Adapter() });

const store = configureStore({ files: initFiles("/home/person@place.tv") }, { files });

describe("DetailedFileSearch", () => {
    test.skip("Detailed File Search component", () => {
        expect(Renderer.create(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Add filename", () => {
        const filename = "nom de file";
        const detailedFileSearchWrapper = mount(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        );
        detailedFileSearchWrapper.find(Button).simulate("click");
        detailedFileSearchWrapper.findWhere(it => it.props().placeholder === "Filename must include...").find("input").simulate("change", { target: { value: filename } });
        expect(detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).state("fileName")).toBe(filename);
    });

    test.skip("Add dates to fields", () => {
        const m = moment();
        const detailedFileSearchWrapper = mount(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        );
        detailedFileSearchWrapper.find(Button).simulate("click");
        detailedFileSearchWrapper.find(DatePicker).forEach(it => it.find("input").simulate("change", { target: { value: m } }));
        expect((detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).state("createdBefore"))).toEqual(m);
        expect((detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).state("createdAfter"))).toEqual(m);
        expect((detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).state("modifiedBefore"))).toEqual(m);
        expect((detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).state("modifiedAfter"))).toEqual(m);
    });

    test.skip("Add date, causing one field to disappear, and render an error message", () => {
        const m1 = moment(new Date());
        const m2 = moment(new Date(new Date().getMilliseconds() - 500));
        const detailedFileSearchWrapper = mount(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        );
        detailedFileSearchWrapper.find(Button).simulate("click");
        detailedFileSearchWrapper.find(DatePicker).first().find("input").simulate("change", { target: { value: m1 } });
        expect((detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).instance().state as any).createdAfter).toBeDefined();
        detailedFileSearchWrapper.find(DatePicker).slice(1, 2).find("input").simulate("change", { target: { value: m2 } });
        expect((detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0).instance().state as any).createdAfter).toBeUndefined();
        // FIXME When error messages are better handled for detailedFileSearch, dismiss error;
    });

    test.skip("Deselect folder and file checkboxes", () => {
        const detailedFileSearch = mount(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        );
        detailedFileSearch.find(Button).simulate("click");
        console.log(detailedFileSearch.find(Label).length);
        const folderCheckbox = detailedFileSearch.find(Label).first();
        const filesCheckbox = detailedFileSearch.find(Label).last();
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFolders).toBe(true);
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFiles).toBe(true);
        filesCheckbox.simulate("click", { target: { checked: false } });
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFolders).toBe(true);
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFiles).toBe(false);
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFolders).toBe(false);
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFiles).toBe(false);
        filesCheckbox.find("input").simulate("click");
        folderCheckbox.find("input").simulate("click");
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFolders).toBe(true);
        expect((detailedFileSearch.find(DetailedFileSearch).childAt(0).state()).allowFiles).toBe(true);
    });

    test.skip("Add sensitivities, clear one, clear all", () => {
        const detailedFileSearchWrapper = mount(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        );
        detailedFileSearchWrapper.find(Button).simulate("click");
        const sensitivityDropdown = detailedFileSearchWrapper.find("Dropdown").findWhere(it => it.props().text === "Add sensitivity level");
        const detailedFileSearchComponent = detailedFileSearchWrapper.find(DetailedFileSearch).childAt(0);
        expect((detailedFileSearchComponent.instance().state as any).sensitivities.has("Open Access")).toBe(false);
        sensitivityDropdown.findWhere(it => it.props().text === "Open Access").simulate("click");
        expect((detailedFileSearchComponent.instance().state as any).sensitivities.has("Open Access")).toBe(true);
        detailedFileSearchWrapper.find(Label).children().first().children().simulate("click");
        expect((detailedFileSearchComponent.instance().state as any).sensitivities.has("Open Access")).toBe(false);
        sensitivityDropdown.findWhere(it => it.props().text === "Sensitive").simulate("click");
        sensitivityDropdown.findWhere(it => it.props().text === "Confidential").simulate("click");
        expect((detailedFileSearchComponent.instance().state as any).sensitivities.size).toBe(2);
        detailedFileSearchWrapper.find(Label).children().last().children().first().simulate("click");
        expect((detailedFileSearchComponent.instance().state as any).sensitivities.size).toBe(0);

    });

    test.skip("Add extensions, clear one, clear all", () => {
        const extensions = ".ext1 .ext2";
        const detailedFileSearchWrapper = mount(
            <Provider store={store}>
                <DetailedFileSearch />
            </Provider>
        );
        detailedFileSearchWrapper.find(Button).simulate("click");
        detailedFileSearchWrapper.find(Input).findWhere(it => it.props().placeholder === "Add extensions...").find("input").simulate("change", { target: { value: extensions } })
        const extensionDropdown = detailedFileSearchWrapper.find("Dropdown").findWhere(it => it.props().text === "Add extension preset");
        // FIXME

    });

    test.skip("Add extensions from presets, clear one, clear all", () => {
        // FIXME
    });

    test.skip("Add tag filename, clear one, clear all", () => {
        // FIXME
    });
});