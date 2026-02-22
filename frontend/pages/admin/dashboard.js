(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.dashboard = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const stats = await window.Api.getAdminStats();

            const html = `
            <h1>관리자 대시보드</h1>
            <div class="stats-grid">
                <div class="stat-card card">
                    <div class="stat-number">${stats.totalUsers}</div>
                    <div class="stat-label">전체 회원</div>
                </div>
                <div class="stat-card card">
                    <div class="stat-number">${stats.totalCourses}</div>
                    <div class="stat-label">등록 과정</div>
                </div>
                <div class="stat-card card">
                    <div class="stat-number">${stats.totalEnrollments}</div>
                    <div class="stat-label">총 수강 신청</div>
                </div>
                <div class="stat-card card">
                    <div class="stat-number">${stats.activeStudents}</div>
                    <div class="stat-label">활성 수강생</div>
                </div>
                <div class="stat-card card">
                    <div class="stat-number">${stats.completedEnrollments}</div>
                    <div class="stat-label">수료 완료</div>
                </div>
                <div class="stat-card card">
                    <div class="stat-number">${stats.completionRate}%</div>
                    <div class="stat-label">수료율</div>
                </div>
                <div class="stat-card card">
                    <div class="stat-number">${stats.totalCertificates}</div>
                    <div class="stat-label">발급 이수증</div>
                </div>
            </div>
            <h2 style="margin-top:32px">관리 메뉴</h2>
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
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
