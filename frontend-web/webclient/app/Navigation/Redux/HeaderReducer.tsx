export const SET_PRIORITIZED_SEARCH = "SET_PRIORITIZED_SEARCH";

const header = (state: any = {}, action) => {
    switch (action.type) {
        case SET_PRIORITIZED_SEARCH: {
            return { ...state, prioritizedSearch: action.priority };
        }
        default: {
            return state;
        }
    }
};

export default header;