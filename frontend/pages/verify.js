(function () {
    window.Pages = window.Pages || {};

    window.Pages.verify = async function (params) {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();

        if (!params.certNumber) {
            app.innerHTML = Components.page(user, `
                <div class="verify-page">
                    <h1>이수증 진위 확인</h1>
                    <form id="verify-form" class="verify-form">
                        <label for="cert-input">이수증 번호</label>
                        <input id="cert-input" type="text" placeholder="CERT-2026-00001" required />
                        <button type="submit" class="btn btn-primary">확인</button>
                    </form>
                    <div id="verify-result"></div>
                </div>
            `);

            document.getElementById("verify-form").addEventListener("submit", (e) => {
                e.preventDefault();
                const number = document.getElementById("cert-input").value.trim();
                if (number) {
                    window.Router.navigate("/verify/" + number);
                }
            });
            return;
        }

        app.innerHTML = Components.page(user, Components.loading());

        try {
            const result = await window.Api.verifyCertificate(params.certNumber);

            let html = '<div class="verify-page"><h1>이수증 진위 확인</h1>';

            if (result.valid) {
                html += `
                <div class="verify-result valid">
                    <div class="verify-badge valid-badge">유효한 이수증</div>
                    <table class="verify-table">
                        <tr><th>이수증 번호</th><td>${Components.escapeHtml(result.certificateNumber)}</td></tr>
                        <tr><th>수료자</th><td>${Components.escapeHtml(result.userName || '-')}</td></tr>
                        <tr><th>과정명</th><td>${Components.escapeHtml(result.courseName)}</td></tr>
                        <tr><th>수료일</th><td>${result.issuedAt ? result.issuedAt.substring(0, 10) : '-'}</td></tr>
                    </table>
                </div>`;
            } else {
                html += `
                <div class="verify-result invalid">
                    <div class="verify-badge invalid-badge">유효하지 않은 이수증</div>
                    <p>이수증 번호 <strong>${Components.escapeHtml(params.certNumber)}</strong>에 해당하는 이수증을 찾을 수 없습니다.</p>
                </div>`;
            }

            html += `<a href="#/verify" class="btn btn-secondary" style="margin-top:16px;">다른 이수증 확인</a></div>`;
            app.innerHTML = Components.page(user, html);
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
