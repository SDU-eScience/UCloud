import * as React from "react";
import {InvokeCommand} from "@/Authentication/DataHook";
import {NavigateFunction} from "react-router";
import {BrowseType} from "@/Resource/BrowseType";
import {Dispatch} from "redux";

export interface ItemRenderer<T, CB = any> {
    Icon?: React.FunctionComponent<{resource?: T, size: string; browseType: BrowseType; callbacks: CB;}>;
    MainTitle?: React.FunctionComponent<{resource?: T; browseType: BrowseType; callbacks: CB;}>;
    Stats?: React.FunctionComponent<{resource?: T; browseType: BrowseType; callbacks: CB;}>;
    ImportantStats?: React.FunctionComponent<{resource?: T; callbacks: CB; browseType: BrowseType}>;
}


// TODO(Jonas): Expand? Change? Should probably be in use for resourcebrowser
export interface StandardCallbacks<T> {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    onSelect?: (resource: T) => void;
    embedded: boolean;
    dispatch: Dispatch;
    navigate: NavigateFunction;
}

