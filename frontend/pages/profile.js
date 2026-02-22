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

                <div class="profile-section card">
                    <h2>이름 변경</h2>
                    <form id="name-form" class="profile-form">
                        <div class="form-row">
                            <input id="new-name" type="text" value="${Components.escapeHtml(me.name || '')}" placeholder="새 이름 입력" required maxlength="100" />
                            <button type="submit" class="btn btn-primary">변경</button>
                        </div>
                        <p id="name-status" class="form-status"></p>
                    </form>
                </div>

                <div class="profile-section card">
                    <h2>비밀번호 변경</h2>
                    <form id="password-form" class="profile-form">
                        <label for="current-pw">현재 비밀번호</label>
                        <input id="current-pw" type="password" required />
                        <label for="new-pw">새 비밀번호 (8자 이상)</label>
                        <input id="new-pw" type="password" required minlength="8" />
                        <label for="confirm-pw">새 비밀번호 확인</label>
                        <input id="confirm-pw" type="password" required minlength="8" />
                        <button type="submit" class="btn btn-primary" style="margin-top:12px">비밀번호 변경</button>
                        <p id="pw-status" class="form-status"></p>
                    </form>
                </div>

                <div class="profile-actions">
                    <a href="#/my-learning" class="btn btn-secondary">내 학습</a>
                    <a href="#/my-certificates" class="btn btn-secondary">내 이수증</a>
                </div>
            </div>`;

            app.innerHTML = Components.page(user, html);

            // Name change
            document.getElementById("name-form").addEventListener("submit", async (e) => {
                e.preventDefault();
                const nameInput = document.getElementById("new-name");
                const status = document.getElementById("name-status");
                const btn = e.target.querySelector("button");
                btn.disabled = true;
                try {
                    await window.Api.updateName(nameInput.value.trim());
                    status.textContent = "이름이 변경되었습니다.";
                    status.className = "form-status success";
                } catch (err) {
                    status.textContent = "변경 실패: " + err.message;
                    status.className = "form-status error";
                }
                btn.disabled = false;
            });

            // Password change
            document.getElementById("password-form").addEventListener("submit", async (e) => {
                e.preventDefault();
                const current = document.getElementById("current-pw").value;
                const newPw = document.getElementById("new-pw").value;
                const confirm = document.getElementById("confirm-pw").value;
                const status = document.getElementById("pw-status");
                const btn = e.target.querySelector("button");

                if (newPw !== confirm) {
                    status.textContent = "새 비밀번호가 일치하지 않습니다.";
                    status.className = "form-status error";
                    return;
                }

                btn.disabled = true;
                try {
                    await window.Api.changePassword(current, newPw);
                    status.textContent = "비밀번호가 변경되었습니다.";
                    status.className = "form-status success";
                    e.target.reset();
                } catch (err) {
                    status.textContent = "변경 실패: " + err.message;
                    status.className = "form-status error";
                }
                btn.disabled = false;
            });
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
