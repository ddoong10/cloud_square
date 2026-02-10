(function () {
    const TOKEN_KEY = "lms_access_token";
    const { API_BASE_URL, STATIC_BASE_URL } = window.APP_CONFIG;

    const authView = document.getElementById("auth-view");
    const appView = document.getElementById("app-view");
    const adminPanel = document.getElementById("admin-panel");

    const showLoginBtn = document.getElementById("show-login-btn");
    const showSignupBtn = document.getElementById("show-signup-btn");

    const loginForm = document.getElementById("login-form");
    const signupForm = document.getElementById("signup-form");
    const adminCreateForm = document.getElementById("admin-create-form");
    const adminSubmitBtn = adminCreateForm.querySelector("button[type='submit']");

    const refreshBtn = document.getElementById("refresh-btn");
    const syncBtn = document.getElementById("sync-btn");
    const logoutBtn = document.getElementById("logout-btn");
    const residentNumberBtn = document.getElementById("resident-number-btn");

    const authStatus = document.getElementById("auth-status");
    const adminResult = document.getElementById("admin-result");
    const meResult = document.getElementById("me-result");
    const residentNumberResult = document.getElementById("resident-number-result");
    const lectureList = document.getElementById("lecture-list");
    const userBadge = document.getElementById("user-badge");
    const staticBase = document.getElementById("static-base");

    const lectureTitleInput = document.getElementById("lecture-title");
    const lectureFileInput = document.getElementById("lecture-file");
    const lectureThumbnailFileInput = document.getElementById("lecture-thumbnail-file");

    let currentUser = null;
    staticBase.textContent = STATIC_BASE_URL;

    showLoginBtn.addEventListener("click", () => showAuthTab("login"));
    showSignupBtn.addEventListener("click", () => showAuthTab("signup"));

    loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const email = document.getElementById("login-email").value.trim();
        const password = document.getElementById("login-password").value;

        try {
            const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, password })
            });

            const data = await parseJson(response);
            if (!response.ok) {
                throw new Error(data.message || "로그인 실패");
            }

            localStorage.setItem(TOKEN_KEY, data.accessToken);
            authStatus.textContent = "로그인 성공";
            authStatus.classList.remove("error");
            await enterApp();
        } catch (error) {
            authStatus.textContent = `로그인 오류: ${error.message}`;
            authStatus.classList.add("error");
        }
    });

    signupForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const email = document.getElementById("signup-email").value.trim();
        const password = document.getElementById("signup-password").value;
        const residentNumber = document.getElementById("signup-resident-number").value.trim();

        try {
            const response = await fetch(`${API_BASE_URL}/api/auth/signup`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ email, password, residentNumber })
            });

            const data = await parseJson(response);
            if (!response.ok) {
                throw new Error(data.message || "회원가입 실패");
            }

            authStatus.textContent = `회원가입 완료: ${data.email}`;
            authStatus.classList.remove("error");
            document.getElementById("login-email").value = data.email;
            document.getElementById("login-password").value = "";
            signupForm.reset();
            showAuthTab("login");
        } catch (error) {
            authStatus.textContent = `회원가입 오류: ${error.message}`;
            authStatus.classList.add("error");
        }
    });

    logoutBtn.addEventListener("click", () => {
        localStorage.removeItem(TOKEN_KEY);
        currentUser = null;
        meResult.textContent = "";
        residentNumberResult.textContent = "";
        lectureList.innerHTML = "";
        setAdminResult("", null);
        userBadge.textContent = "";
        showAuthOnly();
    });

    residentNumberBtn.addEventListener("click", async () => {
        try {
            await loadResidentNumber();
        } catch (error) {
            residentNumberResult.textContent = `주민번호 조회 오류: ${error.message}`;
            residentNumberResult.classList.remove("success");
            residentNumberResult.classList.add("error");
        }
    });

    refreshBtn.addEventListener("click", async () => {
        try {
            await loadLectures();
        } catch (error) {
            setAdminResult(`목록 조회 오류: ${error.message}`, "error");
        }
    });

    syncBtn.addEventListener("click", async () => {
        try {
            ensureAdmin();
            const token = getTokenOrThrow();
            syncBtn.disabled = true;
            setAdminResult("CDN 동기화 시작...", "pending");

            const response = await fetch(`${API_BASE_URL}/api/lectures/sync`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}` }
            });

            const data = await parseJson(response);
            if (!response.ok) {
                throw new Error(data.message || "CDN 동기화 실패");
            }

            setAdminResult(
                `동기화 완료\n스캔: ${data.scannedCount}\n신규 등록: ${data.insertedCount}`,
                "success"
            );
            await loadLectures();
        } catch (error) {
            setAdminResult(`동기화 오류: ${error.message}`, "error");
        } finally {
            syncBtn.disabled = false;
        }
    });

    adminCreateForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        try {
            ensureAdmin();
            const token = getTokenOrThrow();
            const title = lectureTitleInput.value.trim();
            adminSubmitBtn.disabled = true;
            setAdminResult("1/4 요청 확인 중...", "pending");

            if (!title) {
                setAdminResult("강의 제목을 입력하세요.", "error");
                return;
            }
            if (!lectureFileInput.files || lectureFileInput.files.length === 0) {
                setAdminResult("업로드할 영상 파일을 선택하세요.", "error");
                return;
            }

            setAdminResult("2/4 영상 파일 업로드 중...", "pending");
            const uploadFormData = new FormData();
            uploadFormData.append("file", lectureFileInput.files[0]);

            const uploadResponse = await fetch(`${API_BASE_URL}/api/uploads`, {
                method: "POST",
                headers: { Authorization: `Bearer ${token}` },
                body: uploadFormData
            });
            const uploadData = await parseJson(uploadResponse);
            if (!uploadResponse.ok) {
                throw new Error(uploadData.message || "파일 업로드 실패");
            }

            let thumbnailUrl = null;
            if (lectureThumbnailFileInput.files && lectureThumbnailFileInput.files.length > 0) {
                setAdminResult("3/4 썸네일 업로드 중...", "pending");
                const thumbnailFormData = new FormData();
                thumbnailFormData.append("file", lectureThumbnailFileInput.files[0]);

                const thumbnailResponse = await fetch(`${API_BASE_URL}/api/uploads`, {
                    method: "POST",
                    headers: { Authorization: `Bearer ${token}` },
                    body: thumbnailFormData
                });
                const thumbnailData = await parseJson(thumbnailResponse);
                if (!thumbnailResponse.ok) {
                    throw new Error(thumbnailData.message || "썸네일 업로드 실패");
                }
                thumbnailUrl = thumbnailData.url;
                setAdminResult(`3/4 썸네일 업로드 완료\n4/4 강의 등록 중...`, "pending");
            } else {
                setAdminResult(`3/3 강의 등록 중...`, "pending");
            }

            const lectureResponse = await fetch(`${API_BASE_URL}/api/lectures`, {
                method: "POST",
                headers: {
                    Authorization: `Bearer ${token}`,
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ title, videoUrl: uploadData.url, thumbnailUrl })
            });
            const lectureData = await parseJson(lectureResponse);
            if (!lectureResponse.ok) {
                throw new Error(lectureData.message || "강의 등록 실패");
            }

            setAdminResult(
                `등록 완료\nID: ${lectureData.id}\n제목: ${lectureData.title}\n영상 URL: ${lectureData.videoUrl}\n썸네일 URL: ${lectureData.thumbnailUrl || "(없음)"}`,
                "success"
            );
            adminCreateForm.reset();
            await loadLectures();
        } catch (error) {
            setAdminResult(`등록 오류: ${error.message}`, "error");
        } finally {
            adminSubmitBtn.disabled = false;
        }
    });

    bootstrap();

    async function bootstrap() {
        const token = localStorage.getItem(TOKEN_KEY);
        if (!token) {
            showAuthOnly();
            return;
        }

        try {
            await enterApp();
        } catch (_) {
            localStorage.removeItem(TOKEN_KEY);
            showAuthOnly();
        }
    }

    async function enterApp() {
        await loadMe();
        await loadLectures();
        authView.classList.add("hidden");
        appView.classList.remove("hidden");
    }

    function showAuthOnly() {
        appView.classList.add("hidden");
        authView.classList.remove("hidden");
        showAuthTab("login");
        authStatus.textContent = "";
        authStatus.classList.remove("error");
    }

    function showAuthTab(mode) {
        const isLogin = mode === "login";
        loginForm.classList.toggle("hidden", !isLogin);
        signupForm.classList.toggle("hidden", isLogin);
        showLoginBtn.classList.toggle("active", isLogin);
        showSignupBtn.classList.toggle("active", !isLogin);
    }

    async function loadMe() {
        const token = getTokenOrThrow();
        const response = await fetch(`${API_BASE_URL}/api/me`, {
            headers: { Authorization: `Bearer ${token}` }
        });

        const data = await parseJson(response);
        if (!response.ok) {
            throw new Error(data.message || "내 정보 조회 실패");
        }

        currentUser = data;
        meResult.textContent = JSON.stringify(data, null, 2);
        residentNumberResult.textContent = "";
        residentNumberResult.classList.remove("error", "success", "pending");
        userBadge.textContent = `${data.email} (${data.role})`;
        adminPanel.classList.toggle("hidden", data.role !== "ADMIN");
    }

    async function loadResidentNumber() {
        const token = getTokenOrThrow();
        residentNumberResult.textContent = "주민번호 복호화 조회 중...";
        residentNumberResult.classList.remove("error", "success");
        residentNumberResult.classList.add("pending");

        const response = await fetch(`${API_BASE_URL}/api/me/resident-number`, {
            headers: { Authorization: `Bearer ${token}` }
        });
        const data = await parseJson(response);
        if (!response.ok) {
            throw new Error(data.message || "주민번호 조회 실패");
        }

        if (!data.available) {
            residentNumberResult.textContent = "저장된 주민번호가 없습니다.";
        } else {
            residentNumberResult.textContent = [
                `복호화 결과: ${data.residentNumber}`,
                `암호화 저장 여부: ${data.encryptedAtRest ? "YES" : "NO"}`
            ].join("\n");
        }
        residentNumberResult.classList.remove("pending", "error");
        residentNumberResult.classList.add("success");
    }

    async function loadLectures() {
        const token = getTokenOrThrow();
        const response = await fetch(`${API_BASE_URL}/api/lectures`, {
            headers: { Authorization: `Bearer ${token}` }
        });

        const data = await parseJson(response);
        if (!response.ok) {
            throw new Error(data.message || "강의 목록 조회 실패");
        }

        renderLectureList(data);
    }

    function renderLectureList(lectures) {
        lectureList.innerHTML = "";
        if (!Array.isArray(lectures) || lectures.length === 0) {
            const empty = document.createElement("li");
            empty.className = "lecture-empty";
            empty.textContent = "강의가 없습니다. 관리자에서 등록 후 CDN 동기화를 실행하세요.";
            lectureList.appendChild(empty);
            return;
        }

        lectures.forEach((lecture) => {
            const item = document.createElement("li");
            item.className = "lecture-item";

            const thumbnail = createLectureThumbnail(lecture);

            const content = document.createElement("div");
            content.className = "lecture-content";

            const title = document.createElement("span");
            title.className = "lecture-title";
            title.textContent = lecture.title;

            const actions = document.createElement("div");
            actions.className = "lecture-actions";

            const link = document.createElement("a");
            link.className = "lecture-link";
            link.href = lecture.videoUrl;
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            link.textContent = "강의 보기";

            actions.appendChild(link);
            if (currentUser && currentUser.role === "ADMIN") {
                const cryptoBtn = document.createElement("button");
                cryptoBtn.type = "button";
                cryptoBtn.className = "lecture-crypto";
                cryptoBtn.textContent = "암복호화 점검";
                cryptoBtn.addEventListener("click", async () => {
                    await checkLectureCrypto(lecture);
                });
                actions.appendChild(cryptoBtn);

                const deleteBtn = document.createElement("button");
                deleteBtn.type = "button";
                deleteBtn.className = "lecture-delete";
                deleteBtn.textContent = "삭제";
                deleteBtn.addEventListener("click", async () => {
                    await deleteLecture(lecture);
                });
                actions.appendChild(deleteBtn);
            }

            content.appendChild(title);
            content.appendChild(actions);
            item.appendChild(thumbnail);
            item.appendChild(content);
            lectureList.appendChild(item);
        });
    }

    function createLectureThumbnail(lecture) {
        if (lecture.thumbnailUrl) {
            const image = document.createElement("img");
            image.className = "lecture-thumb";
            image.src = lecture.thumbnailUrl;
            image.alt = `${lecture.title} 썸네일`;
            image.loading = "lazy";
            return image;
        }

        const placeholder = document.createElement("div");
        placeholder.className = "lecture-thumb lecture-thumb-placeholder";
        placeholder.textContent = "COURSE PREVIEW";
        return placeholder;
    }

    async function checkLectureCrypto(lecture) {
        try {
            ensureAdmin();
            const token = getTokenOrThrow();
            setAdminResult(`암복호화 점검 중...\n강의 ID: ${lecture.id}`, "pending");

            const response = await fetch(`${API_BASE_URL}/api/lectures/${lecture.id}/crypto-check`, {
                method: "GET",
                headers: { Authorization: `Bearer ${token}` }
            });
            const data = await parseJson(response);
            if (!response.ok) {
                throw new Error(data.message || "암복호화 점검 실패");
            }

            const text = [
                "암복호화 점검 완료",
                `강의 ID: ${data.lectureId}`,
                `videoUrl 암호화 저장: ${data.videoEncryptedAtRest ? "YES" : "NO"}`,
                `videoUrl 복호화 가능: ${data.videoDecryptionOk ? "YES" : "NO"}`,
                `thumbnail 존재: ${data.hasThumbnail ? "YES" : "NO"}`,
                `thumbnail 암호화 저장: ${data.thumbnailEncryptedAtRest ? "YES" : "NO"}`,
                `thumbnail 복호화 가능: ${data.thumbnailDecryptionOk ? "YES" : "NO"}`
            ].join("\n");

            setAdminResult(text, "success");
        } catch (error) {
            setAdminResult(`암복호화 점검 오류: ${error.message}`, "error");
        }
    }

    async function deleteLecture(lecture) {
        try {
            ensureAdmin();
            const shouldDelete = window.confirm(`강의 "${lecture.title}"을(를) 삭제할까요?`);
            if (!shouldDelete) {
                return;
            }

            const token = getTokenOrThrow();
            setAdminResult(`삭제 중...\nID: ${lecture.id}\n제목: ${lecture.title}`, "pending");
            const response = await fetch(`${API_BASE_URL}/api/lectures/${lecture.id}`, {
                method: "DELETE",
                headers: { Authorization: `Bearer ${token}` }
            });

            const data = await parseJson(response);
            if (!response.ok) {
                throw new Error(data.message || "강의 삭제 실패");
            }

            const storageMessage = data.objectDeleteAttempted
                ? (data.objectDeleted
                    ? "Object Storage 파일 삭제도 완료됐습니다."
                    : "DB 삭제는 완료됐고 Object Storage 파일 삭제는 실패했습니다.")
                : "DB 레코드 삭제만 수행했습니다. (외부 URL 혹은 키 확인 불가)";

            const thumbnailStorageMessage = data.thumbnailDeleteAttempted
                ? (data.thumbnailDeleted
                    ? "썸네일 파일 삭제도 완료됐습니다."
                    : "썸네일은 DB 삭제 완료, Object Storage 파일 삭제 실패")
                : "썸네일 파일 삭제는 수행하지 않았습니다.";

            setAdminResult(
                `삭제 완료\n강의 ID: ${data.lectureId}\n${storageMessage}\n${thumbnailStorageMessage}`,
                "success"
            );
            await loadLectures();
        } catch (error) {
            setAdminResult(`삭제 오류: ${error.message}`, "error");
        }
    }

    function ensureAdmin() {
        if (!currentUser || currentUser.role !== "ADMIN") {
            throw new Error("관리자 권한이 필요합니다.");
        }
    }

    function getTokenOrThrow() {
        const token = localStorage.getItem(TOKEN_KEY);
        if (!token) {
            throw new Error("로그인이 필요합니다.");
        }
        return token;
    }

    async function parseJson(response) {
        const text = await response.text();
        if (!text) {
            return {};
        }

        try {
            return JSON.parse(text);
        } catch (_) {
            return { message: text };
        }
    }

    function setAdminResult(message, tone) {
        adminResult.textContent = message;
        adminResult.classList.remove("error", "success", "pending");
        if (tone) {
            adminResult.classList.add(tone);
        }
    }
})();
