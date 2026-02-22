(function () {
    window.Pages = window.Pages || {};

    window.Pages.profile = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const me = await window.Api.fetchMe();

            const roleLabel = { STUDENT: "학생", INSTRUCTOR: "강사", ADMIN: "관리자" };

            let html = `
            <div class="profile-page">
                <h1>마이페이지</h1>
                <div class="profile-card card">
                    <table class="profile-table">
                        <tr><th>이메일</th><td>${Components.escapeHtml(me.email)}</td></tr>
                        <tr><th>이름</th><td>${Components.escapeHtml(me.name || '-')}</td></tr>
                        <tr><th>역할</th><td>${roleLabel[me.role] || me.role}</td></tr>
                    </table>
                </div>
                <div class="profile-actions">
                    <a href="#/my-learning" class="btn btn-secondary">내 학습</a>
                    <a href="#/my-certificates" class="btn btn-secondary">내 이수증</a>
                </div>
            </div>`;

            app.innerHTML = Components.page(user, html);
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
