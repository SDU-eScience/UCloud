import * as React from "react";

// Note(Jonas): The SVG parser/outputters config seems broken, but if they are re-run, these files do not exists as
// svgs, and would therefore be deleted/overwritten if not in separate file.
// So this is a Hack(Jonas).

export const importIcon = (props: any) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 19 24"
        fillRule="evenodd"
        clipRule="evenodd"
        fill="currentcolor"
        {...props}
    >
        <path id="Icon_material-import-export" data-name="Icon material-import-export" d="M12.863,4.5,7.5,9.85h4.022v9.4H14.2V9.85h4.022Zm9.386,18.785v-9.4H19.567v9.4H15.545l5.363,5.35,5.363-5.35Z" transform="translate(-7.5 -4.5)" />
    </svg>
);

export const documentation = (props: any) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 23.3 24"
        fillRule="evenodd"
        clipRule="evenodd"
        fill="currentcolor"
        {...props}
    >
        <path id="Icon_open-document" data-name="Icon open-document" d="M0,0V24H23.3V12H9.984V0ZM13.312,0V9H23.3ZM3.328,6H6.656V9H3.328Zm0,6H6.656v3H3.328Zm0,6H16.64v3H3.328Z" />
    </svg>
);

export function sidebarFiles(props: any) {
    return <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 27"
        fillRule="evenodd"
        clipRule="evenodd"
        fill="currentcolor"
        {...props}
    >
        <path id="Icon_metro-files-empty" data-name="Icon metro-files-empty" d="M26.343,11.064a23.194,23.194,0,0,0-2.23-2.551,23.2,23.2,0,0,0-2.551-2.23A4.081,4.081,0,0,0,19.235,5.2H9.82A2.049,2.049,0,0,0,7.774,7.25V26.08A2.049,2.049,0,0,0,9.82,28.126H25.375a2.049,2.049,0,0,0,2.047-2.047V13.39a4.08,4.08,0,0,0-1.079-2.326ZM22.954,9.67a22.331,22.331,0,0,1,1.857,2.082H20.873V7.814A22.314,22.314,0,0,1,22.954,9.67Zm2.83,16.409a.415.415,0,0,1-.409.409H9.82a.415.415,0,0,1-.409-.409V7.25A.415.415,0,0,1,9.82,6.84h9.415v5.731a.819.819,0,0,0,.819.819h5.731Zm-7.5-23.072a4.081,4.081,0,0,0-2.326-1.08H6.545A2.049,2.049,0,0,0,4.5,3.975V22.8A2.05,2.05,0,0,0,6.136,24.81V3.975a.415.415,0,0,1,.409-.409H19c-.247-.2-.487-.39-.715-.558Z" transform="translate(-3.999 -1.428)" fill="currentColor" stroke="currentColor" strokeWidth="1" />
    </svg>
}

export function sidebarProjects(props: any) {
    return <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 25.795 25.79"
        fillRule="evenodd"
        clipRule="evenodd"
        fill="currentcolor"
        {...props}
    >
        <g id="Icon_ionic-ios-apps" data-name="Icon ionic-ios-apps" transform="translate(0 0.001)">
            <path id="Path_73" data-name="Path 73" d="M29.4,9.443,19.369,4.869a5.434,5.434,0,0,0-3.956,0L5.386,9.443C4.2,9.98,4.2,10.86,5.386,11.4l9.939,4.533a5.71,5.71,0,0,0,4.137,0L29.4,11.4C30.584,10.86,30.584,9.98,29.4,9.443Z" transform="translate(-4.493 -4.498)" />
            <g id="Group_26" data-name="Group 26" transform="translate(0 10.867)">
                <path id="Path_74" data-name="Path 74" d="M15.326,26.233l-6.7-3.056a1.09,1.09,0,0,0-.893,0L5.386,24.245c-1.182.537-1.182,1.417,0,1.954l9.939,4.533a5.71,5.71,0,0,0,4.137,0L29.4,26.2c1.182-.537,1.182-1.417,0-1.954l-2.344-1.068a1.09,1.09,0,0,0-.893,0l-6.7,3.056A5.71,5.71,0,0,1,15.326,26.233Z" transform="translate(-4.493 -16.198)" />
                <path id="Path_75" data-name="Path 75" d="M29.4,16.938l-2.129-.967a1.074,1.074,0,0,0-.886,0l-7.253,3.284a5.929,5.929,0,0,1-3.485,0L8.395,15.971a1.074,1.074,0,0,0-.886,0l-2.129.967c-1.182.537-1.182,1.417,0,1.954l9.939,4.533a5.71,5.71,0,0,0,4.137,0L29.4,18.892C30.584,18.355,30.584,17.475,29.4,16.938Z" transform="translate(-4.493 -15.875)" />
            </g>
        </g>
    </svg >
}

export function sidebarAppStore(props: any) {
    return (
        <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 26.155 26.146"
            fillRule="evenodd"
            clipRule="evenodd"
            fill="currentcolor"
            {...props}
        >
            <path id="Icon_ionic-md-appstore" data-name="Icon ionic-md-appstore" d="M29.53,9.915H22.991a6.539,6.539,0,0,0-13.078,0H3.375c1.175,13.581.817,19.616.817,19.616h24.52S28.355,23.37,29.53,9.915Zm-13.078-4.9a4.9,4.9,0,0,1,4.9,4.9H11.549A4.9,4.9,0,0,1,16.453,5.017ZM13.183,25.359V14.748l8.991,5.306Z" transform="translate(-3.375 -3.385)" />
        </svg>
    );
}

export function sidebarRuns(props: any) {
    return <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 25 25"
        fillRule="evenodd"
        clipRule="evenodd"
        fill="currentcolor"
        {...props}
    >
        <g id="Group_494" data-name="Group 494" transform="translate(-49 -414)">
            <g id="Group_493" data-name="Group 493">
                <rect id="Rectangle_497" data-name="Rectangle 497" width="23" height="23" transform="translate(49 414)" fill="#fff" />
                <rect id="Rectangle_498" data-name="Rectangle 498" width="16" height="15" transform="translate(52 419)" fill="var(--sidebarColor)" />
                <path id="Path_169" data-name="Path 169" d="M6.5,0A6.5,6.5,0,1,1,0,6.5,6.5,6.5,0,0,1,6.5,0Z" transform="translate(61 426)" fill="var(--sidebarColor)" />
                <path id="Icon_awesome-play-circle" data-name="Icon awesome-play-circle" d="M5.6.563A5.042,5.042,0,1,0,10.646,5.6,5.041,5.041,0,0,0,5.6.563ZM7.956,6.092,4.378,8.145a.489.489,0,0,1-.726-.427V3.49a.489.489,0,0,1,.726-.427L7.956,5.238A.489.489,0,0,1,7.956,6.092Z" transform="translate(61.896 426.896)" fill="#fff" />
            </g>
        </g>
    </svg>
}