(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.lectures = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const [courses, lectures] = await Promise.all([
                window.Api.getCourses({ all: true }),
                window.Api.getLectures()
            ]);

            let html = `
            <div class="admin-header">
                <h1>강의 관리</h1>
                <div class="admin-header-actions">
                    <button class="btn btn-primary" id="add-lecture-btn">강의 추가</button>
                    <button class="btn btn-secondary" id="sync-btn">CDN 동기화</button>
                </div>
            </div>
            <div id="admin-status" class="admin-status"></div>
            <table class="admin-table">
                <thead>
                    <tr><th>ID</th><th>제목</th><th>과정</th><th>시간</th><th>순서</th><th>관리</th></tr>
                </thead>
                <tbody>`;

            lectures.forEach(l => {
                const courseName = courses.find(c => c.id === l.courseId)?.title || '-';
                const duration = l.durationSeconds ? Math.floor(l.durationSeconds / 60) + "분" : '-';
                html += `
                <tr>
                    <td>${l.id}</td>
                    <td>${Components.escapeHtml(l.title)}</td>
                    <td>${Components.escapeHtml(courseName)}</td>
                    <td>${duration}</td>
                    <td>${l.sortOrder != null ? l.sortOrder : '-'}</td>
                    <td>
                        <button class="btn-sm btn-danger" onclick="window.Pages.admin._deleteLecture(${l.id})">삭제</button>
                    </td>
                </tr>`;
            });

            html += '</tbody></table>';
            app.innerHTML = Components.page(user, html);

            document.getElementById("sync-btn").addEventListener("click", async () => {
                const status = document.getElementById("admin-status");
                status.textContent = "동기화 중...";
                status.className = "admin-status pending";
                try {
                    const result = await window.Api.syncLectures();
                    status.textContent = `동기화 완료 - 스캔: ${result.scannedCount}, 신규: ${result.insertedCount}`;
                    status.className = "admin-status success";
                    window.Pages.admin.lectures();
                } catch (err) {
                    status.textContent = "동기화 실패: " + err.message;
                    status.className = "admin-status error";
                }
            });

            document.getElementById("add-lecture-btn").addEventListener("click", () => {
                const courseOptions = courses.map(c =>
                    `<option value="${c.id}">${Components.escapeHtml(c.title)}</option>`
                ).join('');

                Components.modal("강의 추가", `
                    <form id="add-lecture-form">
                        <label>제목 *</label>
                        <input name="title" required maxlength="255" />
                        <label>과정</label>
                        <select name="courseId"><option value="">선택 안 함</option>${courseOptions}</select>
                        <label>영상 파일 *</label>
                        <input name="file" type="file" accept="video/*" required />
                        <label>썸네일 (선택)</label>
                        <input name="thumbnail" type="file" accept="image/*" />
                        <label>설명</label>
                        <textarea name="description" rows="2"></textarea>
                        <label>재생 시간 (초)</label>
                        <input name="durationSeconds" type="number" min="0" />
                        <label>정렬 순서</label>
                        <input name="sortOrder" type="number" min="0" />
                        <button type="submit" class="btn btn-primary" style="margin-top:12px">업로드 + 등록</button>
                    </form>
                `);

                document.getElementById("add-lecture-form").addEventListener("submit", async (e) => {
                    e.preventDefault();
                    const form = e.target;
                    const submitBtn = form.querySelector("button[type=submit]");
                    submitBtn.disabled = true;
                    submitBtn.textContent = "업로드 중...";

                    try {
                        const videoUpload = await window.Api.upload(form.file.files[0]);
                        let thumbnailUrl = null;
                        if (form.thumbnail.files.length > 0) {
                            const thumbUpload = await window.Api.upload(form.thumbnail.files[0]);
                            thumbnailUrl = thumbUpload.url;
                        }

                        await window.Api.createLecture({
                            title: form.title.value,
                            videoUrl: videoUpload.url,
                            thumbnailUrl: thumbnailUrl,
                            courseId: form.courseId.value ? parseInt(form.courseId.value) : null,
                            description: form.description.value || null,
                            durationSeconds: form.durationSeconds.value ? parseInt(form.durationSeconds.value) : null,
                            sortOrder: form.sortOrder.value ? parseInt(form.sortOrder.value) : null
                        });

                        document.querySelector(".modal-overlay").remove();
                        window.Pages.admin.lectures();
                    } catch (err) {
                        alert("등록 실패: " + err.message);
                        submitBtn.disabled = false;
                        submitBtn.textContent = "업로드 + 등록";
                    }
                });
            });
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };

    window.Pages.admin._deleteLecture = async function (lectureId) {
        if (!confirm("이 강의를 삭제하시겠습니까?")) return;
        try {
            await window.Api.deleteLecture(lectureId);
            window.Pages.admin.lectures();
        } catch (err) {
            alert("삭제 실패: " + err.message);
        }
    };
})();
