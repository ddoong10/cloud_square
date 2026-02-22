(function () {
    window.Pages = window.Pages || {};

    window.Pages.courseDetail = async function (params) {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const courseId = params.id;
            const [course, lectures] = await Promise.all([
                window.Api.getCourse(courseId),
                window.Api.getLectures(courseId)
            ]);

            let enrollment = null;
            try {
                enrollment = await window.Api.getProgress(courseId);
            } catch (_) {}

            let html = `
            <div class="course-detail">
                <div class="course-detail-header">
                    <div class="course-detail-thumb">
                        ${course.thumbnailUrl
                            ? `<img src="${course.thumbnailUrl}" alt="${Components.escapeHtml(course.title)}">`
                            : '<div class="course-card-placeholder large">COURSE</div>'}
                    </div>
                    <div class="course-detail-info">
                        <h1>${Components.escapeHtml(course.title)}</h1>
                        ${course.category ? `<span class="course-card-category">${Components.escapeHtml(course.category)}</span>` : ''}
                        ${course.instructorName ? `<p class="instructor">강사: ${Components.escapeHtml(course.instructorName)}</p>` : ''}
                        <p class="meta">${lectures.length}개 강의 | 이수 기준: ${course.passCriteria}%</p>
                        ${course.description ? `<p class="description">${Components.escapeHtml(course.description)}</p>` : ''}
                        <div class="course-detail-actions">`;

            if (enrollment) {
                html += Components.progressBar(enrollment.progressPercent);
                if (enrollment.status === "COMPLETED") {
                    html += `<button class="btn btn-primary" onclick="window.Pages._issueCert(${courseId})">이수증 발급</button>`;
                }
                if (lectures.length > 0) {
                    html += `<a href="#/courses/${courseId}/learn/${lectures[0].id}" class="btn btn-primary">학습 시작</a>`;
                }
            } else if (user && user.role === "STUDENT") {
                html += `<button class="btn btn-primary" id="enroll-btn">수강 신청</button>`;
            } else if (!user) {
                html += `<a href="#/login" class="btn btn-primary">로그인 후 수강 신청</a>`;
            }

            html += `</div></div></div>`;

            // Curriculum
            html += `<section class="curriculum"><h2>커리큘럼</h2><ol class="lecture-curriculum">`;
            if (lectures.length === 0) {
                html += '<li class="text-muted">등록된 강의가 없습니다.</li>';
            } else {
                for (const lec of lectures) {
                    const duration = lec.durationSeconds ? Math.floor(lec.durationSeconds / 60) + "분" : "";
                    const canWatch = !!enrollment;
                    html += `
                    <li class="curriculum-item ${canWatch ? 'clickable' : 'locked'}">
                        <a ${canWatch ? `href="#/courses/${courseId}/learn/${lec.id}"` : ''} class="curriculum-link">
                            <span class="curriculum-title">${Components.escapeHtml(lec.title)}</span>
                            <span class="curriculum-meta">${duration}</span>
                        </a>
                    </li>`;
                }
            }
            html += '</ol></section></div>';

            app.innerHTML = Components.page(user, html);

            const enrollBtn = document.getElementById("enroll-btn");
            if (enrollBtn) {
                enrollBtn.addEventListener("click", async () => {
                    try {
                        await window.Api.enroll(courseId);
                        window.Router.navigate("/courses/" + courseId);
                    } catch (err) {
                        alert("수강 신청 실패: " + err.message);
                    }
                });
            }
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };

    window.Pages._issueCert = async function (courseId) {
        try {
            const cert = await window.Api.issueCertificate(courseId);
            window.Router.navigate("/verify/" + cert.certificateNumber);
        } catch (err) {
            alert("이수증 발급 실패: " + err.message);
        }
    };
})();
