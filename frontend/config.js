(function () {
    var isProdHost = window.location.hostname === "lms.uoscholar-server.store";

    window.APP_CONFIG = {
        API_BASE_URL: isProdHost ? "https://api.uoscholar-server.store" : "http://127.0.0.1:8080",
        STATIC_BASE_URL: "https://static.uoscholar-server.store",
        VOD_BASE_URL: isProdHost ? "https://vod.uoscholar-server.store" : "http://127.0.0.1:8080"
    };
})();
