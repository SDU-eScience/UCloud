export { default as Chart } from "./Chart";
export { default as Usage } from "./Usage";
export { default as Breakdown } from "./Breakdown";
export { default as DetailedPage } from "./DetailedPage";

export const detailedPage = (resource: string, subResource: string): string => {
    return `/accounting/${encodeURIComponent(resource)}/${encodeURIComponent(subResource)}`;
};