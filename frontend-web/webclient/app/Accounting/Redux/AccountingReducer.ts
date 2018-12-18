import { Reducer as ReduxReducer } from "redux";
import { Type as ActionType, Tag } from "./AccountingActions";
import { Type as ReduxType, init, ResourceState } from "./AccountingObject";
import { loadableEventToContent } from "LoadableContent";
import { emptyResourceState } from "./AccountingObject";

export interface Reducer {
    accounting: ReduxReducer<ReduxType>
}

function genericReduce(state: ReduxType, resource: string, newPartialState: Partial<ResourceState>): ReduxType {
    const result = JSON.parse(JSON.stringify(state));
    const currentState: ResourceState = result.resources[resource] || emptyResourceState();
    const newState: ResourceState = { ...currentState, ...newPartialState };
    result.resources[resource] = newState;
    return result;
}

const reducer = (state: ReduxType = init().accounting, action: ActionType): ReduxType => {
    switch (action.type) {
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