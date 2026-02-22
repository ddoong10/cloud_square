(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.dashboard = function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();

        const html = `
        <h1>관리자 대시보드</h1>
        <div class="admin-grid">
            <a href="#/admin/courses" class="admin-card card">
                <h3>과정 관리</h3>
                <p>과정 생성, 수정, 삭제 및 공개 설정</p>
            </a>
            <a href="#/admin/lectures" class="admin-card card">
                <h3>강의 관리</h3>
                <p>영상 업로드, 강의 등록 및 정렬</p>
            </a>
            <a href="#/admin/users" class="admin-card card">
                <h3>수강생 관리</h3>
                <p>수강생 목록 조회</p>
            </a>
            <a href="#/admin/certificates" class="admin-card card">
                <h3>이수증 관리</h3>
                <p>발급된 이수증 조회 및 진위 확인</p>
            </a>
        </div>`;

        app.innerHTML = Components.page(user, html);
    };
})();
