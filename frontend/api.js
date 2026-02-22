(function () {
    const TOKEN_KEY = "lms_access_token";
    const USER_KEY = "lms_current_user";
    const { API_BASE_URL } = window.APP_CONFIG;

    let currentUser = null;

    try {
        const saved = localStorage.getItem(USER_KEY);
        if (saved) currentUser = JSON.parse(saved);
    } catch (_) {}

    async function request(path, options) {
        const token = localStorage.getItem(TOKEN_KEY);
        const headers = options.headers || {};
        if (token) {
            headers["Authorization"] = "Bearer " + token;
        }
        if (options.body && typeof options.body === "object" && !(options.body instanceof FormData)) {
            headers["Content-Type"] = "application/json";
            options.body = JSON.stringify(options.body);
        }
        options.headers = headers;

        const response = await fetch(API_BASE_URL + path, options);

        if (response.status === 401) {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
            currentUser = null;
            window.Router.navigate("/login");
            throw new Error("Unauthorized");
        }

        const text = await response.text();
        let data = {};
        if (text) {
            try { data = JSON.parse(text); } catch (_) { data = { message: text }; }
        }

        if (!response.ok) {
            throw new Error(data.message || "Request failed");
        }
        return data;
    }

    window.Api = {
        isLoggedIn() {
            return !!localStorage.getItem(TOKEN_KEY);
        },

        getCurrentUser() {
            return currentUser;
        },

        getToken() {
            return localStorage.getItem(TOKEN_KEY);
        },

        async login(email, password) {
            const data = await request("/api/auth/login", {
                method: "POST",
                body: { email, password }
            });
            localStorage.setItem(TOKEN_KEY, data.accessToken);
            await this.fetchMe();
            return data;
        },

        async signup(email, password, name, residentNumber) {
            return request("/api/auth/signup", {
                method: "POST",
                body: { email, password, name, residentNumber }
            });
        },

        logout() {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
            currentUser = null;
        },

        async fetchMe() {
            const data = await request("/api/me", { method: "GET" });
            currentUser = data;
            localStorage.setItem(USER_KEY, JSON.stringify(data));
            return data;
        },

        // Courses
        async getCourses(params) {
            let query = "";
            if (params) {
                const parts = [];
                if (params.category) parts.push("category=" + encodeURIComponent(params.category));
                if (params.search) parts.push("search=" + encodeURIComponent(params.search));
                if (params.all) parts.push("all=true");
                if (parts.length) query = "?" + parts.join("&");
            }
            return request("/api/courses" + query, { method: "GET" });
        },

        async getCourse(courseId) {
            return request("/api/courses/" + courseId, { method: "GET" });
        },

        async createCourse(data) {
            return request("/api/courses", { method: "POST", body: data });
        },

        async updateCourse(courseId, data) {
            return request("/api/courses/" + courseId, { method: "PUT", body: data });
        },

        async deleteCourse(courseId) {
            return request("/api/courses/" + courseId, { method: "DELETE" });
        },

        // Lectures
        async getLectures(courseId) {
            const query = courseId ? "?courseId=" + courseId : "";
            return request("/api/lectures" + query, { method: "GET" });
        },

        async createLecture(data) {
            return request("/api/lectures", { method: "POST", body: data });
        },

        async deleteLecture(lectureId) {
            return request("/api/lectures/" + lectureId, { method: "DELETE" });
        },

        async syncLectures() {
            return request("/api/lectures/sync", { method: "POST" });
        },

        // Upload
        async upload(file) {
            const formData = new FormData();
            formData.append("file", file);
            return request("/api/uploads", { method: "POST", body: formData });
        },

        async uploadVod(file) {
            const formData = new FormData();
            formData.append("file", file);
            return request("/api/uploads/vod", { method: "POST", body: formData });
        },

        // Enrollment
        async enroll(courseId) {
            return request("/api/courses/" + courseId + "/enroll", { method: "POST" });
        },

        async getProgress(courseId) {
            return request("/api/courses/" + courseId + "/progress", { method: "GET" });
        },

        async getMyLearning() {
            return request("/api/my-learning", { method: "GET" });
        },

        // Heartbeat / Progress
        async heartbeat(lectureId, data) {
            return request("/api/lectures/" + lectureId + "/heartbeat", { method: "POST", body: data });
        },

        async completeLecture(lectureId) {
            return request("/api/lectures/" + lectureId + "/complete", { method: "POST" });
        },

        async getResume(lectureId) {
            return request("/api/lectures/" + lectureId + "/resume", { method: "GET" });
        },

        async getStreamUrl(lectureId) {
            return request("/api/lectures/" + lectureId + "/stream-url", { method: "GET" });
        },

        // Certificates
        async issueCertificate(courseId) {
            return request("/api/courses/" + courseId + "/certificate", { method: "POST" });
        },

        async getCertificate(certNumber) {
            return request("/api/certificates/" + certNumber, { method: "GET" });
        },

        async verifyCertificate(certNumber) {
            return request("/api/certificates/" + certNumber + "/verify", { method: "GET" });
        },

        async getMyCertificates() {
            return request("/api/my-certificates", { method: "GET" });
        },

        async downloadCertificatePdf(certNumber) {
            const token = localStorage.getItem("token");
            const res = await fetch(API_BASE_URL + "/api/certificates/" + certNumber + "/pdf", {
                headers: { "Authorization": "Bearer " + token }
            });
            if (!res.ok) throw new Error("PDF 다운로드 실패");
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement("a");
            a.href = url;
            a.download = certNumber + ".pdf";
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        },

        // Admin
        async getAdminUsers() {
            return request("/api/admin/users", { method: "GET" });
        }
    };
})();
