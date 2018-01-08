<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport"
          content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    <title>Successful Authentication</title>
</head>
<body>

<noscript>JavaScript is required for this page to function correctly.</noscript>

<input type="hidden" value="${accessToken}" id="accessToken">
<input type="hidden" value="${refreshToken}" id="refreshToken">

<script>
    window.sessionStorage.setItem("accessToken", document.getElementById("accessToken").value);
    window.sessionStorage.setItem("refreshToken", document.getElementById("refreshToken").value);
    window.location.href = "/";
</script>

</body>
</html>