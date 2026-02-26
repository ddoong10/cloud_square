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
                    <tr><th>ID</th><th>썸네일</th><th>제목</th><th>카테고리</th><th>강의수</th><th>공개</th><th>관리</th></tr>
                </thead>
                <tbody>`;

            if (courses.length === 0) {
                html += '<tr><td colspan="7" class="text-muted">등록된 과정이 없습니다.</td></tr>';
            } else {
                courses.forEach(c => {
                    html += `
                    <tr>
                        <td>${c.id}</td>
                        <td>${c.thumbnailUrl ? '<img src="' + Components.escapeHtml(Components.resolveUrl(c.thumbnailUrl)) + '" style="width:48px;height:32px;object-fit:cover;border-radius:4px">' : '<span class="text-muted">-</span>'}</td>
                        <td>${Components.escapeHtml(c.title)}</td>
                        <td>${Components.escapeHtml(c.category || '-')}</td>
                        <td>${c.lectureCount}</td>
                        <td>${c.published ? '공개' : '비공개'}</td>
                        <td class="admin-actions-cell">
                            <button class="btn-sm btn-primary" onclick="window.Pages.admin._editCourse(${c.id})">수정</button>
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
                        <label>썸네일 이미지</label>
                        <input name="thumbnail" type="file" accept="image/*" />
                        <button type="submit" class="btn btn-primary" style="margin-top:12px">생성</button>
                    </form>
                `);

                document.getElementById("create-course-form").addEventListener("submit", async (e) => {
                    e.preventDefault();
                    const form = e.target;
                    const submitBtn = form.querySelector("button[type=submit]");
                    submitBtn.disabled = true;
                    submitBtn.textContent = "생성 중...";

                    try {
                        let thumbnailUrl = null;
                        if (form.thumbnail.files.length > 0) {
                            submitBtn.textContent = "썸네일 업로드 중...";
                            const thumbUpload = await window.Api.upload(form.thumbnail.files[0]);
                            thumbnailUrl = thumbUpload.url;
                        }

                        await window.Api.createCourse({
                            title: form.title.value,
                            description: form.description.value || null,
                            category: form.category.value || null,
                            passCriteria: parseInt(form.passCriteria.value) || 80,
                            thumbnailUrl: thumbnailUrl
                        });
                        document.querySelector(".modal-overlay").remove();
                        window.Pages.admin.courses();
                    } catch (err) {
                        alert("생성 실패: " + err.message);
                        submitBtn.disabled = false;
                        submitBtn.textContent = "생성";
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

    window.Pages.admin._editCourse = async function (courseId) {
        try {
            const course = await window.Api.getCourse(courseId);
            Components.modal("과정 수정", `
                <form id="edit-course-form">
                    <label>제목 *</label>
                    <input name="title" required maxlength="255" value="${Components.escapeHtml(course.title)}" />
                    <label>설명</label>
                    <textarea name="description" rows="3">${Components.escapeHtml(course.description || '')}</textarea>
                    <label>카테고리</label>
                    <input name="category" maxlength="100" value="${Components.escapeHtml(course.category || '')}" />
                    <label>이수 기준 (%)</label>
                    <input name="passCriteria" type="number" value="${course.passCriteria || 80}" min="1" max="100" />
                    <label>새 썸네일 이미지 (변경 시)</label>
                    <input name="thumbnail" type="file" accept="image/*" />
                    <button type="submit" class="btn btn-primary" style="margin-top:12px">저장</button>
                </form>
            `);

            document.getElementById("edit-course-form").addEventListener("submit", async (e) => {
                e.preventDefault();
                const form = e.target;
                const submitBtn = form.querySelector("button[type=submit]");
                submitBtn.disabled = true;
                submitBtn.textContent = "저장 중...";

                try {
                    let thumbnailUrl = undefined;
                    if (form.thumbnail.files.length > 0) {
                        const thumbUpload = await window.Api.upload(form.thumbnail.files[0]);
                        thumbnailUrl = thumbUpload.url;
                    }

                    const updateData = {
                        title: form.title.value,
                        description: form.description.value || null,
                        category: form.category.value || null,
                        passCriteria: parseInt(form.passCriteria.value) || 80
                    };
                    if (thumbnailUrl !== undefined) {
                        updateData.thumbnailUrl = thumbnailUrl;
                    }

                    await window.Api.updateCourse(courseId, updateData);
                    document.querySelector(".modal-overlay").remove();
                    window.Pages.admin.courses();
                } catch (err) {
                    alert("수정 실패: " + err.message);
                    submitBtn.disabled = false;
                    submitBtn.textContent = "저장";
                }
            });
        } catch (err) {
            alert("과정 정보 로딩 실패: " + err.message);
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
