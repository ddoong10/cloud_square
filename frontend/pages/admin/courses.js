(function () {
    window.Pages = window.Pages || {};
    window.Pages.admin = window.Pages.admin || {};

    window.Pages.admin.courses = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const courses = await window.Api.getCourses({ all: true });

            let html = `
            <div class="admin-header">
                <h1>과정 관리</h1>
                <button class="btn btn-primary" id="create-course-btn">새 과정 만들기</button>
            </div>
            <table class="admin-table">
                <thead>
                    <tr><th>ID</th><th>제목</th><th>카테고리</th><th>강의수</th><th>공개</th><th>관리</th></tr>
                </thead>
                <tbody>`;

            if (courses.length === 0) {
                html += '<tr><td colspan="6" class="text-muted">등록된 과정이 없습니다.</td></tr>';
            } else {
                courses.forEach(c => {
                    html += `
                    <tr>
                        <td>${c.id}</td>
                        <td>${Components.escapeHtml(c.title)}</td>
                        <td>${Components.escapeHtml(c.category || '-')}</td>
                        <td>${c.lectureCount}</td>
                        <td>${c.published ? '공개' : '비공개'}</td>
                        <td class="admin-actions-cell">
                            <button class="btn-sm btn-secondary" onclick="window.Pages.admin._togglePublish(${c.id}, ${!c.published})">${c.published ? '비공개' : '공개'}</button>
                            <button class="btn-sm btn-danger" onclick="window.Pages.admin._deleteCourse(${c.id})">삭제</button>
                        </td>
                    </tr>`;
                });
            }
            html += '</tbody></table>';

            app.innerHTML = Components.page(user, html);

            document.getElementById("create-course-btn").addEventListener("click", () => {
                Components.modal("새 과정 만들기", `
                    <form id="create-course-form">
                        <label>제목 *</label>
                        <input name="title" required maxlength="255" />
                        <label>설명</label>
                        <textarea name="description" rows="3"></textarea>
                        <label>카테고리</label>
                        <input name="category" maxlength="100" />
                        <label>이수 기준 (%)</label>
                        <input name="passCriteria" type="number" value="80" min="1" max="100" />
                        <label>썸네일 URL</label>
                        <input name="thumbnailUrl" maxlength="2048" />
                        <button type="submit" class="btn btn-primary" style="margin-top:12px">생성</button>
                    </form>
                `);

                document.getElementById("create-course-form").addEventListener("submit", async (e) => {
                    e.preventDefault();
                    const form = e.target;
                    try {
                        await window.Api.createCourse({
                            title: form.title.value,
                            description: form.description.value || null,
                            category: form.category.value || null,
                            passCriteria: parseInt(form.passCriteria.value) || 80,
                            thumbnailUrl: form.thumbnailUrl.value || null
                        });
                        document.querySelector(".modal-overlay").remove();
                        window.Pages.admin.courses();
                    } catch (err) {
                        alert("생성 실패: " + err.message);
                    }
                });
            });
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };

    window.Pages.admin._togglePublish = async function (courseId, publish) {
        try {
            await window.Api.updateCourse(courseId, { published: publish });
            window.Pages.admin.courses();
        } catch (err) {
            alert("변경 실패: " + err.message);
        }
    };

    window.Pages.admin._deleteCourse = async function (courseId) {
        if (!confirm("이 과정을 삭제하시겠습니까?")) return;
        try {
            await window.Api.deleteCourse(courseId);
            window.Pages.admin.courses();
        } catch (err) {
            alert("삭제 실패: " + err.message);
        }
    };
})();
