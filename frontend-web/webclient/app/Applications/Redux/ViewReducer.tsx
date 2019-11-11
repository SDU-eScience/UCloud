import {LoadableEventTag, loadableEventToContent} from "LoadableContent";
import {Reducer as ReduxReducer} from "redux";
import * as Actions from "./ViewActions";
import {init, Type} from "./ViewObject";

export interface Reducer {
    applicationView: ReduxReducer<Type>;
}

const reducer: ReduxReducer<Type> = (state: Type = init().applicationView, action: Actions.Type): Type => {
    switch (action.type) {
        case Actions.Tag.RECEIVE_APP: {
            return {...state, application: loadableEventToContent(action.payload)};
        }

        case Actions.Tag.RECEIVE_PREVIOUS: {
            return {...state, previous: loadableEventToContent(action.payload)};
        }

        case Actions.Tag.RECEIVE_FAVORITE: {
            let application = state.application.content;
            if (action.payload.type === LoadableEventTag.CONTENT && application !== undefined) {
                application = {...application, favorite: !application.favorite};
            }

            return {
                ...state,
                favorite: loadableEventToContent(action.payload),
                application: {...state.application, content: application}
            };
        }

        default: {
            return state;
        }
    }
};

export default reducer;
