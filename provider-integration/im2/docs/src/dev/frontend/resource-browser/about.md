## Resource Browser

The Resource Browser is a component intended to bring a user interface for generic resources, with features a user would expect from a native file explorer. The component is written in Vanilla TypeScript, but is mounted within the React-mounted root, as to be compatible with the rest of the frontend.

Additionally, keyboard navigation, either by arrow-keys or shortcuts, are available when using the component.

### Motivation

The component was written to accomodate rendering directories containing thousands of files, without any significant slowdowns. The component supports keyboard inputs, like navigation and shortcuts. The component replaced the previous custom 'List'-component, that was written in React, which had major slowdowns for larger directories. While packages like `react-window` could be used, this would only fix the performance aspect, and not the wanted interactions like keyboard shortcuts or drag to select. Initially, the component was written with file-browsing in mind, which is why some of the operations relate heavily to functionality to files, but were applicable to a lot of other resources on UCloud.

### Implementation

See [`Use`](./use.md) for a how-to with implementing a component that uses the ResourceBrowser.

The ResourceBrowser-component is, as mentioned, written in TypeScript. The component is a class that handles setting up the HTML-elements the Resource Browser adds and removes resource entries from directly, as opposed to React that keeps a shadow DOM to guage whether updates need to happen. Instead of running checks for differences, different parts of the Resource Browser are targeted for updates when needed, like the rows, operations or filters.

The setup of the ResourceBrowser happens in three steps:

- Constructor
- Init
- Mount

#### Constructor

The constructor sets the members that are expected to be immutable. 

#### Init and Mount

With the Resource Browser instantiated, `init` is called.

<!-- https://www.mermaideditor.io/#pako:eJx9VU1v2zAM_StGdtmAFrDjjzg9DGi7Dj2kGFB0p20HWaISoY5kSEq7oO1_H6XEim21MxA45iOfKPJReplRxWB2MeOteqYbom2yuv8tE3zMrllr0m2Sn9et2rGEqm2nJEh7gN1z-fkeCLUn6MsJu0LMqJ2mcKXVswGdCGkskRQGTpfJ-fnX12_CdMTSDZhkC8aQNZjXq5PT9a_bh7tVAi1scQnzZ7CGD79TTHCBMcn1AQLJ3J_Dx-xsZuy-BTO7eJk1xIB72w1y4a799xm-6ONaq51k16pVGoFP3D-IcSXtd7IV7R7NhkhzjnsRDum02BK9DyEZFClxgAGqJBtCFZvXbImQBW3FEOEZL7lDWiEhWPO8yMpy9oZcHVDcHfVZ77tRq0Zbk9jHq7APT11zwik6eUhpBp6bNqyE7Gh-gL_WGVOeLeYEjQyXM0LJCRXwnC6GcKBjy8UirQZQT7mo8zL15bCY641kY0qWcQLlEA6UabmsquUACllWBeSNL72iqJQJYwOEwwANhKEzR6TnyyAnpbMTjRoN1S8WZenX9-YVaaCdlKMXx8mhpzx2DnM_js8ksm93gEOSy4LkTT2AHoRt4wbhCAi7_yCfIxj3-gB4xkloygtejz2iNYm1WjQ7O4j9wSaViF1unkCOdBh8omJ1j7divWnx5-1NUeapqxIf29O8WhK3HQ0tsag1sxHdCgfHkzX1nFfvgJetzf4bjQ5zr1iaE2AThz7XqlgUtdMeHnhKj4sY9HXABrrLSU16e88UatahugUVHZH2g8kdesRNHaBRy1rBYXSkhKjjERvK1vfgaI-bT6148uX4YNwGDiHH0Ayp7FRx_VniofgcceboDGmV6j6YJA_FU-TM0V7su_LG8zreXuhBhKJcxqN8cIgXI-ZxzBmq4qH4eHLmSCW-vPAQkc3LKodm7HCiZAUwVwaG93IcG8QQ4LiAVAtUF2njaEbn1byaugSGZpnRzFUOL00wFlcYh4cxO-EhNlyUay1OlzHMoeapK5BiZL8ayvqYyxs-_wALksye -->
