(function () {
    const R = window.Router;

    // Public routes
    R.register("/login", () => window.Pages.auth(), {});
    R.register("/verify", (p) => window.Pages.verify(p), {});
    R.register("/verify/:certNumber", (p) => window.Pages.verify(p), {});

    // Authenticated routes
    R.register("/", () => {
        if (!window.Api.isLoggedIn()) { R.navigate("/login"); return; }
        window.Pages.home();
    }, {});
    R.register("/courses", () => window.Pages.courses(), { auth: true });
    R.register("/courses/:id", (p) => window.Pages.courseDetail(p), { auth: true });
    R.register("/courses/:courseId/learn/:lectureId", (p) => window.Pages.learn(p), { auth: true });
    R.register("/my-learning", () => window.Pages.myLearning(), { auth: true });
    R.register("/my-certificates", () => window.Pages.myCertificates(), { auth: true });
    R.register("/profile", () => window.Pages.profile(), { auth: true });

    // Admin routes
    R.register("/admin/dashboard", () => window.Pages.admin.dashboard(), { auth: true, roles: ["ADMIN", "INSTRUCTOR"] });
    R.register("/admin/courses", () => window.Pages.admin.courses(), { auth: true, roles: ["ADMIN", "INSTRUCTOR"] });
    R.register("/admin/lectures", () => window.Pages.admin.lectures(), { auth: true, roles: ["ADMIN", "INSTRUCTOR"] });
    R.register("/admin/users", () => window.Pages.admin.users(), { auth: true, roles: ["ADMIN"] });
    R.register("/admin/certificates", () => window.Pages.admin.certificates(), { auth: true, roles: ["ADMIN"] });

    R.start();
})();
