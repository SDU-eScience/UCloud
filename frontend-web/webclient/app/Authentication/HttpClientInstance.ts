import HttpClient from "./lib";
import {WebSocketFactory} from "./ws";

export let Client = new HttpClient();
export let WSFactory = new WebSocketFactory(Client);
