import HttpClient from "./lib";
import {WebSocketFactory} from "./ws";

export const Client = new HttpClient();
export const WSFactory = new WebSocketFactory(Client);
