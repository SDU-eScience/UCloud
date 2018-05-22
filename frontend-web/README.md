# SDUCloud front-end `frontend-web`

This service provides a web interface to access the the logged in user's data, and functionality for manipulation of said data.

### Prerequisites

1. npm
2. IntelliJ IDEA (for running authentication service)

## Setup

The `front-end/web` service is currently deployed at `https://cloud.sdu.dk` that handles logging in to the system. The `front-end/webclient` currently proxies to the live server.

As for running the `front-end/webclient` application, navigate to the folder of the application and run `npm install`.

## How to Run

The front-end requires running two services, specifically `front-end/web` and `front-end/webclient`. As expressed in _setup_, the `front-end/web` is deployed, and usually only the `front-end/webclient` will need to be run. This is done by navigating to the folder of the `webclient` application and running the command `npm run start`, which will expose a development enviroment of the application at `localhost:9000/app/dashboard`.

## Using the Site

After logging in to the site, the user will initially be presented with the dashboard. This provides an overview of a favorite files subset, 10 most recently used files and results of jobs that the user is associated with.

The sidebar, if present, depending on the size of the device, will show Files, Applications, Publishing, and Shares. 

The files component provides access to the user's files. Navigation is done by clicking on folders or clicking on a breadcrumb path. For any file, with few exceptions<sup>†</sup>, consisting of moving, renaming, copying, sharing, and deleting a folder or file. A file will also have a detailed breakdown of its info which can be accessed by clicking the Properties link for a file. Additionally, a user can create a folder in the currently accessed folder.

The Applications options contains two nested options, the first of which is Run, which will show a list of available applications, consisting of name and version. Clicking on the Run-button will transfer the user to a form where one can enter actual parameters to use for running the given application. Running the application will then lead to a page providing detailed info on the current job, showing the result of stdout and stderr, if available.

The second option is results, which will show a list of the results of the jobs associated with the user. Clicking on the job id will show a detailed report of the specific job, as mentioned previously.

Publishing provides tools for publishing to Zenodo. Publications will list an overview of publishings, with the possiblity of a more details for a specific upload, by clicking on the "Show More"-button. If the files for the publication have successfully been uploaded to Zenodo, "Finish publication at Zendodo" will transfer the user to Zendodo to finish the publication. 

The Publish option allows the user to upload a series of files to Zenodo from SDUCloud, by selecting them and supplying a name for the publication. 


<sup>†</sup> - the folders named Uploads, Jobs and Favorites has some functionality disallowed, as the user is not allowed to move, rename or delete the given folder.