import SDUCloud from "./lib";
import { WebSocketFactory } from "./ws";

export let Cloud = new SDUCloud();
export let WSFactory = new WebSocketFactory(Cloud);
