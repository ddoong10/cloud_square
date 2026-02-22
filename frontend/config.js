(function () {
    var host = window.location.hostname;
    var isProd = host === "lms.uoscholar-server.store"
        || host === "static.uoscholar-server.store"
        || host.endsWith(".ncloudstorage.com");

    window.APP_CONFIG = {
        API_BASE_URL: isProd ? "https://lms.uoscholar-server.store" : "http://127.0.0.1:8080",
        STATIC_BASE_URL: "https://static.uoscholar-server.store",
        VOD_BASE_URL: isProd ? "https://vod.uoscholar-server.store" : "http://127.0.0.1:8080"
    };
})();
