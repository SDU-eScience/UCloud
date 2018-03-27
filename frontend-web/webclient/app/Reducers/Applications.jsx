export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";

const applications = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS: {
            return { ...state, applications: action.applications }
        }
        default: {
            return state;
        }
    }
};

export default applications;