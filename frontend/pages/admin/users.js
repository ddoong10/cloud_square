(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.users = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const users = await window.Api.getAdminUsers();

            const roleLabel = { ADMIN: "관리자", INSTRUCTOR: "강사", STUDENT: "수강생" };
            const roleCounts = {};
            users.forEach(u => {
                const r = u.role || "STUDENT";
                roleCounts[r] = (roleCounts[r] || 0) + 1;
            });

            let html = `
            <div class="admin-header">
                <h1>수강생 관리</h1>
                <div class="admin-header-actions">
                    <span class="text-muted">총 ${users.length}명</span>
                </div>
            </div>
            <div style="display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap">`;

            Object.entries(roleCounts).forEach(([role, count]) => {
                html += `<span class="course-card-category">${roleLabel[role] || role}: ${count}</span>`;
            });

            html += `</div>
            <table class="admin-table">
                <thead>
                    <tr><th>ID</th><th>이메일</th><th>이름</th><th>역할</th><th>가입일</th></tr>
                </thead>
                <tbody>`;

            if (users.length === 0) {
                html += '<tr><td colspan="5" class="text-muted">등록된 사용자가 없습니다.</td></tr>';
            } else {
                users.forEach(u => {
                    const role = roleLabel[u.role] || u.role || '-';
                    const created = u.createdAt ? u.createdAt.substring(0, 10) : '-';
                    html += `
                    <tr>
                        <td>${u.id}</td>
                        <td>${Components.escapeHtml(u.email)}</td>
                        <td>${Components.escapeHtml(u.name || '-')}</td>
                        <td>${role}</td>
                        <td>${created}</td>
                    </tr>`;
                });
            }

            html += '</tbody></table>';
            app.innerHTML = Components.page(user, html);
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
