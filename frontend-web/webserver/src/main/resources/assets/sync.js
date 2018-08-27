function copy() {
    console.log("Test");
    var input = document.getElementById("refreshToken");
    input.focus();
    console.log(input);
    input.setSelectionRange(0, input.value.length + 1);
    document.execCommand('copy');
}