(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.users = function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();

        const html = `
        <h1>수강생 관리</h1>
        <p class="text-muted">수강생 관리 기능은 추후 업데이트 예정입니다.</p>
        <a href="#/admin/dashboard" class="btn btn-secondary">대시보드로 돌아가기</a>`;

        app.innerHTML = Components.page(user, html);
    };
})();
