# UCloud frontend 

## UCloud description

For a description of where UCloud frontend exists in the context of the UCloud platform, see [introduction](../../overview/introduction.md#introduction).

## Technologies

The UCloud frontend is written in Typescript, with the frontend library [React](https://en.wikipedia.org/wiki/React_(software)). Some shared application state is managed using a custom solution `ExternalStore`, that components can connect to, using the `useSyncExternalStore`-hook provided by React. TypeScript was chosen to reduce the friction that comes from Vanilla JavaScript's dynamic nature, and TypeScript letting developers use upcoming TC-39 features, by compiling to an older target, making UCloud available for people running older versions of JavaScript. 

`npm` is used as the package manager, and contains the various scripts for [running the UCloud frontend](#running-the-ui), [generating icons](#color-references-and-icons), etc. Drop-in replacements can be used if preferred.

React was chosen because of its ubiquity and being battle-tested and the library authors use of "dogfooding". [`React Router`](https://en.wikipedia.org/wiki/React_Router), used for navigation, also has a large userbase and is under a large organisation that uses it, like with React.

Where the performance of React has not been adequate, Vanilla TypeScript/ECMAScript has been used instead. An example of this is the [ResourceBrowser](https://github.com/SDU-eScience/UCloud/blob/master/frontend-web/webclient/app/ui-components/ResourceBrowser.tsx) component, where very little use of React is present.

## Project structure

The UCloud frontend is found in the [`frontend-web/webclient`](https://github.com/SDU-eScience/UCloud/blob/master/frontend-web/webclient/). Configuration files, tests and `node_modules` reside at the base, along with the `app` folder containing the code for the web interface. The contents of the `app` folder is subdivided into folders containing different contexts, i.e. resource types and component groupings.
