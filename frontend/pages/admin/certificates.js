(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.certificates = function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();

        let html = `
        <h1>이수증 관리</h1>
        <div class="verify-form" style="margin-bottom:24px">
            <label for="admin-cert-search">이수증 번호로 검색</label>
            <div style="display:flex;gap:8px">
                <input id="admin-cert-search" type="text" placeholder="CERT-2026-00001" />
                <button class="btn btn-primary" id="admin-cert-search-btn">검색</button>
            </div>
        </div>
        <div id="admin-cert-result"></div>
        <a href="#/admin/dashboard" class="btn btn-secondary">대시보드로 돌아가기</a>`;

        app.innerHTML = Components.page(user, html);

        document.getElementById("admin-cert-search-btn").addEventListener("click", async () => {
            const number = document.getElementById("admin-cert-search").value.trim();
            const resultDiv = document.getElementById("admin-cert-result");
            if (!number) return;

            resultDiv.innerHTML = Components.loading();
            try {
                const result = await window.Api.verifyCertificate(number);
                if (result.valid) {
                    resultDiv.innerHTML = `
                    <div class="verify-result valid">
                        <div class="verify-badge valid-badge">유효</div>
                        <p>수료자: ${Components.escapeHtml(result.userName || '-')}</p>
                        <p>과정: ${Components.escapeHtml(result.courseName)}</p>
                        <p>발급일: ${result.issuedAt ? result.issuedAt.substring(0, 10) : '-'}</p>
                    </div>`;
                } else {
                    resultDiv.innerHTML = '<div class="verify-result invalid"><div class="verify-badge invalid-badge">유효하지 않음</div></div>';
                }
            } catch (err) {
                resultDiv.innerHTML = Components.error(err.message);
            }
        });
    };
})();
