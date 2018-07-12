import SDUCloud from "./lib";

export let Cloud = process.env.NODE_ENV !== 'production' ? 
    new SDUCloud("http://localhost:9000", "local-dev") :
    new SDUCloud("https://cloud.sdu.dk", "web");

