import SDUCloud from '../src/lib'
import $ from 'jquery'

$(() => main());

function main() {
    let cloud = new SDUCloud("https://httpbin.org/anything", "local-dev");
    console.log(cloud);

    $("#open-login").click(() => cloud.openBrowserLoginPage());

    $("#call-get").click(() => {
        cloud.get("files?path=/home/test")
            .then((data) => console.log(data))
            .fail((data) => {
                console.log("We failed :-(");
                console.log(data);
            });
    });

    $("#call-post").click(() => {
        cloud.post("notsure").then((data) => console.log(data))
    });

    $("#call-post-with-body").click(() => {
        cloud.post("notsure", {a: 42}).then((data) => console.log(data))
    });

    $("#create-expired-token").click(() => {
        let dummyJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiw" +
            "iYWRtaW4iOnRydWUsImV4cCI6MH0.g0sTVQA9agQx3eg-X7VTM_ZWXozHwx88jNMPXPW-1So";
        let dummyRefreshToken = "i refresh stuff";

        cloud.setTokens(dummyJwt, dummyRefreshToken);
    });

    $("#create-valid-token").click(() => {
        let jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWR" +
            "taW4iOnRydWUsImV4cCI6MTUxMzk2OTk4OX0.KhLwGaxEltklUPhI3v3rn7kcnV3o7Ipb4-Ue9DMz2Hg";
        let refreshToken = "i refresh stuff valid";

        cloud.setTokens(jwt, refreshToken);
    });

    $("#who-are-we").click(() => {
        console.log(cloud.userInfo);
    });

    $("#print-tokens").click(() => {
        console.log(SDUCloud.storedAccessToken);
        console.log(SDUCloud.storedRefreshToken);
    });
}