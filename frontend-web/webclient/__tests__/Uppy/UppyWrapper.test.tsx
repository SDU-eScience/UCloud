import * as Enzyme from "enzyme";
import Adapter from "enzyme-adapter-react-16";
import uppy from "Uppy/Redux/UppyReducers";
import { initUppy } from "DefaultObjects";
import * as UppyActions from "Uppy/Redux/UppyActions";
import { Cloud } from "Authentication/SDUCloudObject";
import * as ReduxUtilities from "Utilities/ReduxUtilities";

Enzyme.configure({ adapter: new Adapter() });

const uppyStore = ReduxUtilities.configureStore({ uppy: initUppy(Cloud) }, { uppy });

describe("UppyWrapper", () => {
    test("Closed in initial store", () =>
        expect(uppyStore.getState().uppy.uppyOpen).toBeFalsy()
    );

    test("Uppy is defined on store creation", () =>
        expect(uppyStore.getState().uppy.uppy).toBeDefined()
    );

    test("Opening and closing Uppy", () => {
        uppyStore.dispatch(UppyActions.openUppy(true));
        expect(uppyStore.getState().uppy.uppyOpen).toBeTruthy();
        uppyStore.dispatch(UppyActions.closeUppy(uppyStore.getState().uppy.uppy));
    });
});