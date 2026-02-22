(function () {
    window.Pages = window.Pages || {};

    window.Pages.myLearning = async function () {
        const app = document.getElementById("app");
        const user = window.Api.getCurrentUser();
        app.innerHTML = Components.page(user, Components.loading());

        try {
            const enrollments = await window.Api.getMyLearning();

            const inProgress = enrollments.filter(e => e.status === "IN_PROGRESS");
            const completed = enrollments.filter(e => e.status === "COMPLETED");

            let html = '<h1>내 학습</h1>';

            html += '<section class="my-section"><h2>학습 중 (' + inProgress.length + ')</h2>';
            if (inProgress.length === 0) {
                html += '<p class="text-muted">학습 중인 과정이 없습니다. <a href="#/courses">과정 둘러보기</a></p>';
            } else {
                html += '<div class="enrollment-grid">';
                inProgress.forEach(e => {
                    html += `<div class="enrollment-card-wrapper">`;
                    html += Components.enrollmentCard(e);
                    html += `<button class="btn btn-danger btn-sm unenroll-btn" data-course-id="${e.courseId}">수강 취소</button>`;
                    html += `</div>`;
                });
                html += '</div>';
            }
            html += '</section>';

            html += '<section class="my-section"><h2>수료 완료 (' + completed.length + ')</h2>';
            if (completed.length === 0) {
                html += '<p class="text-muted">수료한 과정이 없습니다.</p>';
            } else {
                html += '<div class="enrollment-grid">';
                completed.forEach(e => { html += Components.enrollmentCard(e); });
                html += '</div>';
            }
            html += '</section>';

            app.innerHTML = Components.page(user, html);

            document.querySelectorAll(".unenroll-btn").forEach(btn => {
                btn.addEventListener("click", async (e) => {
                    e.stopPropagation();
                    const courseId = btn.dataset.courseId;
                    if (!confirm("수강을 취소하시겠습니까? 학습 진행 기록이 모두 삭제됩니다.")) return;
                    btn.disabled = true;
                    btn.textContent = "취소 중...";
                    try {
                        await window.Api.unenroll(courseId);
                        alert("수강이 취소되었습니다.");
                        window.Pages.myLearning();
                    } catch (err) {
                        alert("수강 취소 실패: " + err.message);
                        btn.disabled = false;
                        btn.textContent = "수강 취소";
                    }
                });
            });
        } catch (err) {
            app.innerHTML = Components.page(user, Components.error(err.message));
        }
    };
})();
