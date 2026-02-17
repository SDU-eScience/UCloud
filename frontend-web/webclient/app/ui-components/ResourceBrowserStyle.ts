import {injectStyle, makeClassName} from "@/Unstyled";

let didInject = false;

const BrowserClass = makeClassName("browser");
export function injectResourceBrowserStyle(rowSize: number) {
    if (!didInject) injectStyle("ignored", () => `
        body[data-cursor=not-allowed] * {
            cursor: not-allowed !important;
        }

        body[data-cursor=grabbing] * {
            cursor: grabbing !important;
        }
        
        body[data-no-select=true] * {
            user-select: none;
            -webkit-user-select: none;
        }

        ${BrowserClass.dot} .drag-indicator {
            position: fixed;
            z-index: 10000;
            background-color: var(--primaryLight);
            opacity: 20%;
            display: none;
            top: 0;
            left: 0;
        }

        ${BrowserClass.dot} .file-drag-indicator-content,
        ${BrowserClass.dot} .file-drag-indicator {
            position: fixed;
            z-index: 10000;
            display: none;
            top: 0;
            left: 0;
            height: ${rowSize}px;
            user-select: none;
            -webkit-user-select: none;
        }
        
        ${BrowserClass.dot} .file-drag-indicator-content {
            z-index: 10001;
            width: 400px;
            margin: 16px;
            white-space: pre;
        }
        
        ${BrowserClass.dot} .favorite > img {
            display: none;
        }

        ${BrowserClass.dot} .favorite[data-favorite="false"] {
            content: '';
            width: 20px;
        }

        ${BrowserClass.dot} .row .favorite[data-favorite="true"] > img, ${BrowserClass.dot} .row:hover .favorite > img {
            display: block;
        }

        ${BrowserClass.dot} header[data-has-filters] .filters, 
        ${BrowserClass.dot} header[data-has-filters] .session-filters, 
        ${BrowserClass.dot} header[data-has-filters] .right-sort-filters {
            display: flex;
            margin-top: 12px;
        }

        ${BrowserClass.dot} .file-drag-indicator-content img {
            margin-right: 8px;
        }

        ${BrowserClass.dot} .file-drag-indicator {
            background: var(--rowActive);
            color: var(--textPrimary);
            width: 400px;
            overflow: hidden;
            border-radius: 6px;
        }
     
        ${BrowserClass.dot} .file-drag-indicator.animate {
        }

        ${BrowserClass.dot} {
            width: 100%;
            height: calc(100vh - 32px - var(--termsize, 0px));
            display: flex;
            flex-direction: column;
            font-size: 16px;
        }

        ${BrowserClass.dot} header .header-first-row {
            display: flex;
            align-items: center;
            margin-bottom: 8px;
        }

        ${BrowserClass.dot} header .header-first-row img {
            cursor: pointer;
            flex-shrink: 0;
            margin-left: 16px;
        }

        ${BrowserClass.dot} header .header-first-row .location-bar {
            flex-grow: 1;
        }

        .header-first-row .search-icon[data-shown] {
            z-index: 1;
            width: 24px;
            height: 24px;
        }

        ${BrowserClass.dot} header ul {
            padding: 0;
            margin: 0;
            display: flex;
            flex-direction: row;
            gap: 8px;
            height: 35px;
            white-space: pre;
            align-items: center;
        }
        
        ${BrowserClass.dot} header[data-no-gap] ul {
            gap: 0;
        }

        ${BrowserClass.dot} > div {
            flex-grow: 1;
        }

        ${BrowserClass.dot} header {
            width: 100%;
            height: 92px;
            flex-shrink: 0;
            overflow: hidden;
        }
        
        ${BrowserClass.dot} header[data-has-filters], ${BrowserClass.dot} header[data-has-allocations] {
            height: 136px;
        }

        ${BrowserClass.dot} header[data-has-filters][data-has-allocations] {
            height: 162px;
        }

        ${BrowserClass.dot} header .location-bar,
        ${BrowserClass.dot} header.show-location-bar ul {
            display: none;
        }

        ${BrowserClass.dot} header.show-location-bar .location-bar,
        ${BrowserClass.dot} header ul {
            display: flex;
        }

        ${BrowserClass.dot} .location-bar {
            width: 100%;
            font-size: 110%;
            height: 35px;
            font-feature-settings: unset;
        }

        ${BrowserClass.dot} header[has-location-bar] .location:hover {
            border: 1px solid var(--borderColorHover);
        }

        ${BrowserClass.dot} header[has-location-bar] .location:focus-within {
            border-color: var(--primaryMain);
        }
        
        ${BrowserClass.dot} header[has-location-bar] .location li:hover {
            user-select: none;
            -webkit-user-select: none;
        }
        
        ${BrowserClass.dot} header[has-location-bar] .location li:hover {
            cursor: pointer;
            text-decoration: underline;
        }
        
        ${BrowserClass.dot} header[has-location-bar] .location {
            flex-grow: 1;
            border: 1px solid var(--borderColor);
            margin-left: -6px;
            border-radius: 5px;
            width: 100%;
            cursor: text;
            height: 35px;
            transition: margin-right 0.2s;
        }

        ${BrowserClass.dot} header[has-location-bar] .location[in-modal] {
            max-width: 480px;
            overflow-x: clip;
        }
        
        ${BrowserClass.dot} header[has-location-bar] .location input {
            outline: none;
            border: 0;
            height: 32px;
            margin-top: 1px;
            margin-left: 5px;
            background: transparent;
            color: var(--textPrimary);
        }

        ${BrowserClass.dot} header:not([has-location-bar]) > div.header-first-row > div.location {
            cursor: default;
        }

        ${BrowserClass.dot} header input.search-field {
            width: 100%;
            height: 35px;
            margin-left: 5px;
        }

        ${BrowserClass.dot} header div.search-field-wrapper {
            position: relative;
            right: -46px;
            width: 400px;
            display: flex;
            transition: width .2s;
        }

        ${BrowserClass.dot} header div.search-field-wrapper > input.search-field[data-hidden] {
            padding: 0;
        }

        ${BrowserClass.dot} header .search-field-wrapper:has(> input.search-field[data-hidden]) {
            width: 0;
        }
        
        /* If not hidden, make of for the relative position */
        ${BrowserClass.dot} header .search-field-wrapper:not(:has(> input.search-field[data-hidden])) {
            margin-left: -46px;
        }

        ${BrowserClass.dot} header .search-field-wrapper > input.search-field[data-hidden] {
            border: none;
        }
                    
        ${BrowserClass.dot} header > div > div > ul {
            margin-top: 0px;
        }

        ${BrowserClass.dot} header[has-location-bar] > div > div > ul {
            margin-left: 7px;
        }
        
        ${BrowserClass.dot} header > div > div > ul[data-no-slashes="true"] li::before {
            display: inline-block;
            content: unset;
            margin: 0;
        }

        ${BrowserClass.dot} header > div > div > ul li::before {
            display: inline-block;
            content: '/';
            text-decoration: none !important;
        }

        ${BrowserClass.dot} header div ul li {
            list-style: none;
            margin: 0;
            margin-bottom: 1px;
            padding: 0;
            cursor: pointer;
            font-size: 110%;
        }

        ${BrowserClass.dot} .row {
            display: flex;
            flex-direction: row;
            container-type: inline-size;
            height: ${rowSize}px;
            width: 100%;
            align-items: center;
            border-top: 0.5px solid var(--borderColor);
            user-select: none;
            -webkit-user-select: none;
            padding: 0 8px;
            transition: filter 0.3s;
        }

        ${BrowserClass.dot} .row:first-of-type {
            border-top: 0px;
        }
        
        ${BrowserClass.dot} .rows-title {
            max-height: 40px;
            height: 40px;
            color: var(--textPrimary);
            display: none;
            background-color: var(--backgroundDisabled);
            border-radius: 6px 6px 0 0;
            padding-top: 8px;
            padding-bottom: 8px;
            font-size: 110%;
            border-bottom: 1.5px solid var(--borderColor);
            border-top: 0;
        }
        
        body[data-cursor=grabbing] ${BrowserClass.dot} .row:hover {
            background-color: var(--rowHover);
        }

        ${BrowserClass.dot} .row.hidden {
            display: none;
        }

        ${BrowserClass.dot} .row[data-selected="true"] {
            /* NOTE(Dan): We only have an active state, as a result we just use the hover variable. As the active 
               variable is intended for differentiation between the two. This is consistent with how it is used in
               the Tree component */
            background: var(--rowHover); 
        }

        ${BrowserClass.dot} .row .title{
            display: flex;
            align-items: center;

            white-space: pre;
                                                                                                                    /* v favoriteIcon-width */
            width: calc(var(--rowWidth) - var(--stat1Width) - var(--stat2Width) - var(--stat3Width) - var(--stat4Width) - var(--favoriteWidth) - 32px);
            padding-right: 8px; /* So the title doesn't rub up against the second column */
        }
        
        @media screen and (max-width: 860px) {
            ${BrowserClass.dot} .row .title {
                width: calc(var(--rowWidth) - var(--stat1Width) - 38px - var(--favoriteWidth) - var(--favoriteWidth) - 16px);
            }
        }
        

        
        ${BrowserClass.dot} .stat-wrapper {
            width: calc(var(--stat1Width) + var(--stat2Width) + var(--stat3Width) + var(--stat4Width));
            justify-content: end;
            display: flex;
            gap: 8px;
        }

        @media screen and (max-width: 860px) {
            ${BrowserClass.dot} .stat-wrapper {
                width: calc(var(--stat1Width));
            }
        }

        ${BrowserClass.dot} .row .stat2,
        ${BrowserClass.dot} .row .stat3,
        ${BrowserClass.dot} .row .stat4  {
            display: none;
            width: 0;
        }

        @media screen and (min-width: 860px) {
            ${BrowserClass.dot} .row .stat1,
            ${BrowserClass.dot} .row .stat2,
            ${BrowserClass.dot} .row .stat3 {
                display: flex;
                justify-content: center;
                margin-top: auto;
                margin-bottom: auto;
                text-align: center;
            }

            ${BrowserClass.dot} .row .stat1 {
                width: var(--stat1Width);
            }
            
            ${BrowserClass.dot} .row .stat2 {
                width: var(--stat2Width);
            }

            ${BrowserClass.dot} .row .stat3 {
                width: var(--stat3Width);
            }

            ${BrowserClass.dot} .row .stat4 {
                display: flex;
                justify-content: end;
                text-align: end;
                width: var(--stat4Width);
            }
        }

        @media screen and (max-width: 860px) {
            ${BrowserClass.dot} .row .stat1 {
                margin-left: auto;
            }
        }


        ${BrowserClass.dot} .sensitivity-badge {
            height: 2em;
            width: 2em;
            display: flex;
            align-items: center;
            justify-content: center;
            border: 0.2em solid var(--badgeColor);
            border-radius: 100%;
        }

        ${BrowserClass.dot} .sensitivity-badge.PRIVATE {
            --badgeColor: var(--borderColor);
        }

        ${BrowserClass.dot} .sensitivity-badge.CONFIDENTIAL {
            --badgeColor: var(--warningMain);
        }

        ${BrowserClass.dot} .sensitivity-badge.SENSITIVE {
            --badgeColor: var(--errorMain);
        }

        ${BrowserClass.dot} .operation {
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        ${BrowserClass.dot} .operations {
            display: flex;
            flex-direction: row;
            gap: 8px;
        }

        ${BrowserClass.dot} .context-menu {
            position: fixed;
            z-index: 10000;
            top: 0;
            left: 0;
            border-radius: 8px;
            border: 1px solid #E2DDDD;
            cursor: pointer;
            background: var(--backgroundDefault);
            box-shadow: var(--defaultShadow);
            width: 400px;
            display: none;
            overflow-y: auto;
            transition: opacity 120ms, transform 60ms;
        }

        ${BrowserClass.dot} .context-menu ul {
            padding: 0;
            margin: 0;
            display: flex;
            flex-direction: column;
        }

        ${BrowserClass.dot} .context-menu li {
            margin: 0;
            padding: 8px 8px;
            list-style: none;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        ${BrowserClass.dot} .${ShortcutClass} {
            font-family: var(--sansSerif);
        }
        
        @media screen and (max-width: 800px) {
            ${BrowserClass.dot} .${ShortcutClass}, ${BrowserClass.dot} .ShortCutPlusSymbol {
                display: none;
            }
        }

        ${BrowserClass.dot} .HideShortcuts {
            display: none;
        }

        ${BrowserClass.dot} .context-menu li[data-selected=true] {
            background: var(--rowHover);
        }

        ${BrowserClass.dot} .context-menu > ul > *:first-child,
        ${BrowserClass.dot} .context-menu > ul > li:first-child > button {
            border-top-left-radius: 8px;
            border-top-right-radius: 8px;
        }
        
        ${BrowserClass.dot} .context-menu > ul > *:last-child,
         ${BrowserClass.dot} .context-menu > ul > li:last-child,
         ${BrowserClass.dot} .context-menu > ul > li:last-child > button {
            border-bottom-left-radius: 8px;
            border-bottom-right-radius: 8px;
        }
        
        ${BrowserClass.dot} .rename-field {
            display: none;
            position: absolute;
            width: calc(var(--rowWidth) - var(--stat1Width) - var(--stat2Width) - var(--stat3Width) - var(--stat4Width) - var(--favoriteWidth) - 92px);
            background-color: var(--backgroundDefault);
            border-radius: 5px;
            border: 1px solid var(--borderColor);
            outline: 0;
            color: var(--textPrimary);
            z-index: 1;
            left: 12px;
        }

        @media screen and (max-width: 860px) {
            ${BrowserClass.dot} .rename-field {
                width: calc(var(--rowWidth) - var(--stat1Width) - var(--favoriteWidth) - 118px);
            }
        }

        ${BrowserClass.dot} .page-empty {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            height: 200px;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            gap: 16px;
            text-align: center;
        }
        
        ${BrowserClass.dot} .page-empty .graphic {
            background: var(--primaryMain);
            min-height: 100px;
            min-width: 100px;
            border-radius: 100px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        
        ${BrowserClass.dot} .page-empty .provider-reason {
            font-style: italic;
        }

        ${BrowserClass.dot} div > div.right-sort-filters {
            margin-left: auto;
        }
        
        ${BrowserClass.dot} .refresh-icon {
            transition: transform 0.5s;
        }
        
        ${BrowserClass.dot} .refresh-icon:hover {
            transform: rotate(45deg);
        }
    `);
    didInject = true;
    return BrowserClass;
}

export const ShortcutClass = injectStyle("shortcut", k => `
    ${k} {
        color: var(--textPrimary);
        background-color: var(--shortcutBackground);
        border-radius: 5px;
        border: .5px solid var(--shortcutBorderColor);
        border-bottom: 2px solid var(--shortcutBorderColor);
        mix-blend-mode: invert;
        font-size: 12px;
        min-width: 18px;
        height: 18px;
        display: flex;
        align-items: center;
        justify-content: center;
        user-select: none;
        -webkit-user-select: none;
        padding: 0 5px;
    }
    
    html.light ${k} {
        --shortcutBackground: var(--backgroundDefault);
        --shortcutBorderColor: var(--gray-70);
    }
    
    html.dark ${k} {
        --shortcutBackground: var(--backgroundDefault);
        --shortcutBorderColor: var(--gray-60);
    }
`);