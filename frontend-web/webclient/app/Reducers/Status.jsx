export const UPDATE_PAGE_TITLE = "UPDATE_PAGE_TITLE";
export const UPDATE_STATUS = "UPDATE_STATUS";

const status = (state = [], action) => {
    switch (action.type) {
        case UPDATE_PAGE_TITLE: {
            return { ...state, title: action.title }
        }
        case UPDATE_STATUS: {
            return { ...state, status: action.status }
        }
        default: {
            return state;
        }
    }
};

export default status;