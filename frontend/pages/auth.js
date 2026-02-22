(function () {
    window.Pages = window.Pages || {};

    window.Pages.auth = function () {
        const app = document.getElementById("app");
        app.innerHTML = `
        <div class="auth-view">
            <div class="auth-copy">
                <p class="eyebrow">GYEONGGI TRAFFIC TRAINING LMS</p>
                <h1>경기도교통연수원 학습관리시스템</h1>
                <p>로그인 후 강의자료를 조회하고, 수강 신청 및 이수증 발급을 받을 수 있습니다.</p>
            </div>
            <div class="auth-card card">
                <div class="tab-row">
                    <button id="show-login-btn" class="tab-btn active" type="button">로그인</button>
                    <button id="show-signup-btn" class="tab-btn" type="button">회원가입</button>
                </div>
                <form id="login-form">
                    <label for="login-email">이메일</label>
                    <input id="login-email" type="email" placeholder="demo@lms.local" required />
                    <label for="login-password">비밀번호</label>
                    <input id="login-password" type="password" placeholder="비밀번호 입력" required />
                    <button type="submit">로그인</button>
                </form>
                <form id="signup-form" class="hidden">
                    <label for="signup-email">이메일</label>
                    <input id="signup-email" type="email" placeholder="user@example.com" required />
                    <label for="signup-name">이름</label>
                    <input id="signup-name" type="text" placeholder="홍길동" />
                    <label for="signup-password">비밀번호</label>
                    <input id="signup-password" type="password" placeholder="8자 이상" required />
                    <label for="signup-resident-number">주민등록번호</label>
                    <input id="signup-resident-number" type="text" placeholder="901010-1234567" required />
                    <button type="submit">회원가입</button>
                </form>
                <p id="auth-status" class="status"></p>
            </div>
        </div>`;

        const loginForm = document.getElementById("login-form");
        const signupForm = document.getElementById("signup-form");
        const showLoginBtn = document.getElementById("show-login-btn");
        const showSignupBtn = document.getElementById("show-signup-btn");
        const authStatus = document.getElementById("auth-status");

        function showTab(mode) {
            const isLogin = mode === "login";
            loginForm.classList.toggle("hidden", !isLogin);
            signupForm.classList.toggle("hidden", isLogin);
            showLoginBtn.classList.toggle("active", isLogin);
            showSignupBtn.classList.toggle("active", !isLogin);
        }

        showLoginBtn.addEventListener("click", () => showTab("login"));
        showSignupBtn.addEventListener("click", () => showTab("signup"));

        loginForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const email = document.getElementById("login-email").value.trim();
            const password = document.getElementById("login-password").value;
            try {
                await window.Api.login(email, password);
                window.Router.navigate("/");
            } catch (err) {
                authStatus.textContent = "로그인 오류: " + err.message;
                authStatus.classList.add("error");
            }
        });

        signupForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const email = document.getElementById("signup-email").value.trim();
            const name = document.getElementById("signup-name").value.trim();
            const password = document.getElementById("signup-password").value;
            const residentNumber = document.getElementById("signup-resident-number").value.trim();
            try {
                await window.Api.signup(email, password, name || null, residentNumber);
                authStatus.textContent = "회원가입 완료! 로그인해 주세요.";
                authStatus.classList.remove("error");
                showTab("login");
            } catch (err) {
                authStatus.textContent = "회원가입 오류: " + err.message;
                authStatus.classList.add("error");
            }
        });
    };
})();
