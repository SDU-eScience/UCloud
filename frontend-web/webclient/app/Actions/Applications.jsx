import { RECEIVE_APPLICATIONS } from "../Reducers/Applications";

export const fetchFiles = (path, sorting, sortAscending) =>
  Cloud.get(`files?path=${path}`).then(({ response }) => {
    response.forEach(file => file.isChecked = false);
    if (sorting) {
      response = sorting(response, sortAscending);
    }
    return receiveFiles(response);
  });


export const fetchApplications = () =>
    Cloud.get().then(({ response }) => {
        console.log(response);
        response.sort((a, b) => 
            a.prettyName.localeCompare(b.prettyName)
        );
        receiveApplications(response);
    });

const receiveApplications = (applications) => ({
    type: RECEIVE_APPLICATIONS,
    applications
});