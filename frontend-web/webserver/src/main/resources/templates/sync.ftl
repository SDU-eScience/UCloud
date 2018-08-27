<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport"
          content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
    <meta http-equiv="X-UA-Compatible" content="ie=edge">
    <title>ABC2.0 Sync Token</title>

    <link href="https://stackpath.bootstrapcdn.com/bootstrap/4.1.1/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-WskhaSGFgHYWDcbwN70/dfYBj47jz9qbsMId/iRN3ewGhXQFZCSftd1LZCfmhktB" crossorigin="anonymous">

    <link href="https://stackpath.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css" rel="stylesheet"
          integrity="sha384-wvfXpqpZZVQGK6TAh5PVlGOfQNHSoD2xbE+QkPxCAFlNEevoEH3Sl0sibVcOQVnN" crossorigin="anonymous">
</head>
<body class="bg-light">

<div class="container">
    <div class="py-5 text-center">
        <h2>ABC2.0 ↔ SDUCloud</h2>
        <p class="lead">
            Please copy & paste the token below when prompted
        </p>
    </div>

    <div class="row">
        <div class="col-md-12">
            <div class="input-group">
                <input type="text" class="form-control" value="${refreshToken}" id="refreshToken">
                <div class="input-group-append">
                    <button id="copy" class="btn btn-outline-secondary" type="button" onclick="copy()">
                        <i class="fa fa-files-o" aria-hidden="true"></i>
                    </button>
                </div>
            </div>
        </div>
    </div>

    <script src="/api/sync-callback/sync.js"></script>
</body>
</html>
