(function () {
    const routes = [];
    let currentCleanup = null;

    window.Router = {
        register(path, handler, options) {
            routes.push({ path, handler, options: options || {} });
        },

        navigate(path) {
            window.location.hash = path;
        },

        start() {
            window.addEventListener("hashchange", () => this._resolve());
            this._resolve();
        },

        _resolve() {
            const hash = window.location.hash.slice(1) || "/";

            if (currentCleanup) {
                try { currentCleanup(); } catch (_) {}
                currentCleanup = null;
            }

            for (const route of routes) {
                const params = this._match(route.path, hash);
                if (params !== null) {
                    if (route.options.auth && !window.Api.isLoggedIn()) {
                        this.navigate("/login");
                        return;
                    }
                    if (route.options.roles) {
                        const user = window.Api.getCurrentUser();
                        if (!user || !route.options.roles.includes(user.role)) {
                            this.navigate("/");
                            return;
                        }
                    }
                    const result = route.handler(params);
                    if (typeof result === "function") {
                        currentCleanup = result;
                    }
                    return;
                }
            }

            // 404 fallback
            const app = document.getElementById("app");
            if (app) {
                app.innerHTML = '<div class="page-container"><h2>페이지를 찾을 수 없습니다</h2><a href="#/">홈으로</a></div>';
            }
        },

        _match(pattern, hash) {
            const patternParts = pattern.split("/");
            const hashParts = hash.split("/");

            if (patternParts.length !== hashParts.length) return null;

            const params = {};
            for (let i = 0; i < patternParts.length; i++) {
                if (patternParts[i].startsWith(":")) {
                    params[patternParts[i].slice(1)] = decodeURIComponent(hashParts[i]);
                } else if (patternParts[i] !== hashParts[i]) {
                    return null;
                }
            }
            return params;
        }
    };
})();
