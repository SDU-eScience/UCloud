import {Reducer as ReduxReducer} from "redux";
import {Tag, Type as ActionType} from "./AccountingActions";
import {emptyResourceState, init, ResourceState, Type as ReduxType} from "./AccountingObject";
import {emptyLoadableContent, loadableEventToContent} from "LoadableContent";

export interface Reducer {
    accounting: ReduxReducer<ReduxType>
}

function genericReduce(state: ReduxType, resource: string, newPartialState: Partial<ResourceState>): ReduxType {
    const result = JSON.parse(JSON.stringify(state));
    const currentState: ResourceState = result.resources[resource] || emptyResourceState();
    result.resources[resource] = {...currentState, ...newPartialState};
    return result;
}

const reducer = (state: ReduxType = init().accounting, action: ActionType): ReduxType => {
    switch (action.type) {
        case Tag.CLEAR_RESOURCE: {
            return genericReduce(state, action.payload.resource, {
                chart: emptyLoadableContent(),
                usage: emptyLoadableContent(),
                events: emptyLoadableContent()
            })
        }

        case Tag.RECEIVE_CHART: {
            return genericReduce(state, action.payload.resource,
                { chart: loadableEventToContent(action.payload.event) });
        }

        case Tag.RECEIVE_EVENTS: {
            return genericReduce(state, action.payload.resource,
                { events: loadableEventToContent(action.payload.event) });
        }

        case Tag.RECEIVE_USAGE: {
            return genericReduce(state, action.payload.resource,
                { usage: loadableEventToContent(action.payload.event) });
        }

        default: {
            return state;
        }
    }
};

export default reducer;