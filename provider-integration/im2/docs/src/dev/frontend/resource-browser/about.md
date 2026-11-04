## Resource Browser

The Resource Browser is a component intended to bring a user interface for generic resources, with features a user would expect from a native file explorer. The component is written in Vanilla TypeScript, but is mounted within the React-mounted root, as to be compatible with the rest of the frontend.

Additionally, keyboard navigation, either by arrow-keys or shortcuts, are available when using the component.

### Motivation

The component was written to accomodate rendering directories containing thousands of files, without any significant slowdowns. The component supports keyboard inputs, like navigation and shortcuts. The component replaced the previous custom 'List'-component, that was written in React, which had major slowdowns for larger directories. While packages like `react-window` could be used, this would only fix the performance aspect, and not the wanted interactions like keyboard shortcuts or drag to select. Initially, the component was written with file-browsing in mind, which is why some of the operations relate heavily to functionality to files, but were applicable to a lot of other resources on UCloud.

### Implementation

See <TODO-LINK>USE</TODO-LINK> for a how-to with implementing a component that uses the ResourceBrowser.

The ResourceBrowser-component is, as mentioned, written in TypeScript. The component is a normal <What is a normal class?> class that handles setting up the HTML-elements the Resource Browser adds and removes resource entries from.

The <TODO> init, mount, after
